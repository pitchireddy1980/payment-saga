package com.paymentsaga.paymentservice.service;

import com.paymentsaga.common.events.*;
import com.paymentsaga.common.kafka.KafkaEventPublisher;
import com.paymentsaga.paymentservice.entity.PaymentTransaction;
import com.paymentsaga.paymentservice.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Service - Payment Processing and Refunds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final KafkaEventPublisher eventPublisher;
    private final PaymentGatewayClient paymentGatewayClient;
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private static final String SAGA_COMPENSATION_TOPIC = "saga-compensation";

    /**
     * Process payment with retry logic
     */
    @Transactional
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public void processPayment(RiskCheckCompletedEvent event) {
        String orderId = event.getPayload().getOrderId();
        
        if (!event.getPayload().getApproved()) {
            log.info("Skipping payment for order {}: Risk check not approved", orderId);
            return;
        }

        log.info("Processing payment for order: {}", orderId);

        String transactionId = UUID.randomUUID().toString();
        
        // Create transaction record
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId(transactionId)
                .orderId(orderId)
                .sagaId(event.getSagaId())
                .amount(BigDecimal.ZERO) // Would come from order context
                .currency("USD")
                .status(PaymentTransaction.TransactionStatus.PROCESSING)
                .build();

        transactionRepository.save(transaction);

        try {
            // Call payment gateway
            PaymentGatewayResponse response = paymentGatewayClient.processPayment(transaction);

            // Update transaction
            transaction.setStatus(PaymentTransaction.TransactionStatus.COMPLETED);
            transaction.setGatewayTransactionId(response.getGatewayTransactionId());
            transaction.setAuthCode(response.getAuthCode());
            transactionRepository.save(transaction);

            // Publish success event
            publishPaymentProcessed(event, transaction);

        } catch (Exception e) {
            log.error("Payment failed for order: {}", orderId, e);
            
            transaction.setStatus(PaymentTransaction.TransactionStatus.FAILED);
            transaction.setErrorMessage(e.getMessage());
            transactionRepository.save(transaction);

            // Publish failure event
            publishPaymentFailed(event, orderId, e.getMessage());
        }
    }

    /**
     * Refund payment - Compensating transaction
     */
    @Transactional
    public void refundPayment(String sagaId, String reason) {
        transactionRepository.findBySagaId(sagaId)
                .ifPresent(transaction -> {
                    if (transaction.getStatus() != PaymentTransaction.TransactionStatus.COMPLETED) {
                        log.info("No refund needed for order {}: Payment not completed", transaction.getOrderId());
                        return;
                    }

                    if (transaction.getStatus() == PaymentTransaction.TransactionStatus.REFUNDED) {
                        log.info("Payment already refunded for order: {}", transaction.getOrderId());
                        return;
                    }

                    try {
                        log.info("Refunding payment for order: {}", transaction.getOrderId());

                        // Call payment gateway to refund
                        String refundId = paymentGatewayClient.refund(transaction);

                        // Update transaction
                        transaction.setStatus(PaymentTransaction.TransactionStatus.REFUNDED);
                        transaction.setRefundId(refundId);
                        transactionRepository.save(transaction);

                        // Publish refund event
                        publishPaymentRefunded(transaction, reason);

                    } catch (Exception e) {
                        log.error("Refund failed for order: {}. Manual intervention required.", 
                                transaction.getOrderId(), e);
                    }
                });
    }

    private void publishPaymentProcessed(RiskCheckCompletedEvent originalEvent, PaymentTransaction transaction) {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_PROCESSED)
                .timestamp(LocalDateTime.now())
                .sagaId(originalEvent.getSagaId())
                .correlationId(originalEvent.getCorrelationId())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new PaymentProcessedEvent.PaymentProcessedPayload(
                        transaction.getOrderId(),
                        transaction.getTransactionId(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        LocalDateTime.now()))
                .build();

        eventPublisher.publishEvent(PAYMENT_EVENTS_TOPIC, event);
        log.info("Payment processed successfully for order: {}", transaction.getOrderId());
    }

    private void publishPaymentFailed(RiskCheckCompletedEvent originalEvent, String orderId, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_FAILED)
                .timestamp(LocalDateTime.now())
                .sagaId(originalEvent.getSagaId())
                .correlationId(originalEvent.getCorrelationId())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new PaymentFailedEvent.PaymentFailedPayload(
                        orderId,
                        reason,
                        "PAYMENT_GATEWAY_ERROR"))
                .build();

        eventPublisher.publishEvent(PAYMENT_EVENTS_TOPIC, event);
    }

    private void publishPaymentRefunded(PaymentTransaction transaction, String reason) {
        PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_REFUNDED)
                .timestamp(LocalDateTime.now())
                .sagaId(transaction.getSagaId())
                .correlationId(UUID.randomUUID().toString())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new PaymentRefundedEvent.PaymentRefundedPayload(
                        transaction.getOrderId(),
                        transaction.getTransactionId(),
                        transaction.getRefundId(),
                        transaction.getAmount(),
                        reason))
                .build();

        eventPublisher.publishEvent(SAGA_COMPENSATION_TOPIC, event);
        log.info("Payment refunded for order: {}", transaction.getOrderId());
    }

    private BaseEvent.EventMetadata createMetadata() {
        BaseEvent.EventMetadata metadata = new BaseEvent.EventMetadata();
        metadata.setRetryCount(0);
        metadata.setMaxRetries(3);
        metadata.setTimeoutMs(15000L);
        metadata.setSource("payment-service");
        return metadata;
    }
}

/**
 * Payment Gateway Client (simulated)
 */
@Service
@Slf4j
class PaymentGatewayClient {

    public PaymentGatewayResponse processPayment(PaymentTransaction transaction) {
        log.info("Calling payment gateway for transaction: {}", transaction.getTransactionId());
        
        // Simulate processing delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 10% failure rate
        if (Math.random() < 0.1) {
            throw new RuntimeException("Payment gateway timeout");
        }

        return new PaymentGatewayResponse(
                UUID.randomUUID().toString(),
                "APPROVED",
                generateAuthCode()
        );
    }

    public String refund(PaymentTransaction transaction) {
        log.info("Refunding transaction: {}", transaction.getTransactionId());
        
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "REF-" + UUID.randomUUID().toString();
    }

    private String generateAuthCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

@lombok.Data
@lombok.AllArgsConstructor
class PaymentGatewayResponse {
    private String gatewayTransactionId;
    private String status;
    private String authCode;
}
