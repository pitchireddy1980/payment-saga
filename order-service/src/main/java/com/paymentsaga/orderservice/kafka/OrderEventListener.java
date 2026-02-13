package com.paymentsaga.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentsaga.common.events.*;
import com.paymentsaga.common.kafka.KafkaEventPublisher;
import com.paymentsaga.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event listeners for Order Service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;
    private final KafkaEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final String SAGA_COMPENSATION_TOPIC = "saga-compensation";

    /**
     * Listen to Risk Check Completed events
     */
    @KafkaListener(
        topics = "risk-events",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRiskCheckCompleted(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received risk event with key: {}", key);
            
            BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
            
            if (event.getEventType() == EventType.RISK_CHECK_COMPLETED) {
                handleRiskCheckCompletedEvent(objectMapper.convertValue(event, RiskCheckCompletedEvent.class));
            } else if (event.getEventType() == EventType.RISK_CHECK_FAILED) {
                handleRiskCheckFailedEvent(objectMapper.convertValue(event, RiskCheckFailedEvent.class));
            }
            
            // Manual acknowledgment
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing risk event", e);
            throw new RuntimeException("Failed to process risk event", e);
        }
    }

    /**
     * Listen to Payment events
     */
    @KafkaListener(
        topics = "payment-events",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received payment event with key: {}", key);
            
            BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
            
            if (event.getEventType() == EventType.PAYMENT_PROCESSED) {
                handlePaymentProcessedEvent(objectMapper.convertValue(event, PaymentProcessedEvent.class));
            } else if (event.getEventType() == EventType.PAYMENT_FAILED) {
                handlePaymentFailedEvent(objectMapper.convertValue(event, PaymentFailedEvent.class));
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment event", e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }

    private void handleRiskCheckCompletedEvent(RiskCheckCompletedEvent event) {
        log.info("Processing RiskCheckCompleted event for order: {}", 
                event.getPayload().getOrderId());

        if (event.getPayload().getApproved()) {
            // Update order to processing
            orderService.updateOrderToProcessing(event.getSagaId());
            log.info("Risk check approved for saga: {}", event.getSagaId());
        } else {
            // Risk declined - initiate compensation
            log.warn("Risk check declined for saga: {}", event.getSagaId());
            cancelOrder(event.getSagaId(), "Risk check declined");
        }
    }

    private void handleRiskCheckFailedEvent(RiskCheckFailedEvent event) {
        log.warn("Risk check failed for order: {}, reason: {}", 
                event.getPayload().getOrderId(),
                event.getPayload().getReason());

        cancelOrder(event.getSagaId(), "Risk check failed: " + event.getPayload().getReason());
    }

    private void handlePaymentProcessedEvent(PaymentProcessedEvent event) {
        log.info("Payment processed successfully for order: {}, transaction: {}", 
                event.getPayload().getOrderId(),
                event.getPayload().getTransactionId());

        orderService.confirmOrder(event.getSagaId(), event.getPayload().getTransactionId());
    }

    private void handlePaymentFailedEvent(PaymentFailedEvent event) {
        log.warn("Payment failed for order: {}, reason: {}", 
                event.getPayload().getOrderId(),
                event.getPayload().getReason());

        cancelOrder(event.getSagaId(), "Payment failed: " + event.getPayload().getReason());
    }

    /**
     * Compensating transaction - Cancel order and publish event
     */
    private void cancelOrder(String sagaId, String reason) {
        // Cancel order in database
        orderService.cancelOrder(sagaId, reason);

        // Publish OrderCancelled event for other services to compensate
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.ORDER_CANCELLED)
                .timestamp(LocalDateTime.now())
                .sagaId(sagaId)
                .correlationId(UUID.randomUUID().toString())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new OrderCancelledEvent.OrderCancelledPayload(
                        null, // orderId - will be fetched by other services
                        reason,
                        LocalDateTime.now()))
                .build();

        eventPublisher.publishEvent(SAGA_COMPENSATION_TOPIC, event);

        log.info("Order cancelled and compensation event published for saga: {}", sagaId);
    }

    private BaseEvent.EventMetadata createMetadata() {
        BaseEvent.EventMetadata metadata = new BaseEvent.EventMetadata();
        metadata.setRetryCount(0);
        metadata.setMaxRetries(3);
        metadata.setTimeoutMs(15000L);
        metadata.setSource("order-service");
        return metadata;
    }
}
