package com.paymentsaga.orderservice.service;

import com.paymentsaga.common.events.BaseEvent;
import com.paymentsaga.common.events.EventType;
import com.paymentsaga.common.events.PaymentInitiatedEvent;
import com.paymentsaga.common.kafka.KafkaEventPublisher;
import com.paymentsaga.orderservice.dto.CreateOrderRequest;
import com.paymentsaga.orderservice.dto.OrderResponse;
import com.paymentsaga.orderservice.entity.Order;
import com.paymentsaga.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order Service - Saga Initiator
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaEventPublisher eventPublisher;
    
    private static final String PAYMENT_SAGA_TOPIC = "payment-saga";

    /**
     * Initiate payment saga
     */
    @Transactional
    public OrderResponse initiatePayment(CreateOrderRequest request) {
        log.info("Initiating payment for user: {}", request.getUserId());

        // Generate saga ID
        String sagaId = UUID.randomUUID().toString();

        // Create order
        Order order = Order.builder()
                .userId(request.getUserId())
                .sagaId(sagaId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(Order.OrderStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .build();

        order = orderRepository.save(order);
        log.info("Order created: {}", order.getId());

        // Publish PaymentInitiatedEvent
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_INITIATED)
                .timestamp(LocalDateTime.now())
                .sagaId(sagaId)
                .correlationId(UUID.randomUUID().toString())
                .version("1.0")
                .metadata(createMetadata("order-service"))
                .payload(PaymentInitiatedEvent.PaymentInitiatedPayload.builder()
                        .orderId(order.getId())
                        .userId(request.getUserId())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .paymentMethod(request.getPaymentMethod().name())
                        .items(mapToOrderItems(request.getItems()))
                        .build())
                .build();

        eventPublisher.publishEvent(PAYMENT_SAGA_TOPIC, event);

        log.info("Payment saga initiated for order: {}", order.getId());

        return mapToResponse(order);
    }

    /**
     * Get order by ID
     */
    public OrderResponse getOrder(String orderId, String userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return mapToResponse(order);
    }

    /**
     * Update order status to processing (called after risk check)
     */
    @Transactional
    public void updateOrderToProcessing(String sagaId) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new RuntimeException("Order not found for sagaId: " + sagaId));

        order.setStatus(Order.OrderStatus.PROCESSING);
        orderRepository.save(order);

        log.info("Order {} updated to PROCESSING", order.getId());
    }

    /**
     * Confirm order (called after payment success)
     */
    @Transactional
    public void confirmOrder(String sagaId, String transactionId) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new RuntimeException("Order not found for sagaId: " + sagaId));

        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setTransactionId(transactionId);
        orderRepository.save(order);

        log.info("Order {} confirmed with transaction: {}", order.getId(), transactionId);
    }

    /**
     * Cancel order - Compensating transaction
     */
    @Transactional
    public void cancelOrder(String sagaId, String reason) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new RuntimeException("Order not found for sagaId: " + sagaId));

        // Idempotency check
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            log.info("Order {} already cancelled", order.getId());
            return;
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        orderRepository.save(order);

        log.info("Order {} cancelled: {}", order.getId(), reason);
    }

    private BaseEvent.EventMetadata createMetadata(String source) {
        BaseEvent.EventMetadata metadata = new BaseEvent.EventMetadata();
        metadata.setRetryCount(0);
        metadata.setMaxRetries(3);
        metadata.setTimeoutMs(15000L);
        metadata.setSource(source);
        return metadata;
    }

    private List<PaymentInitiatedEvent.OrderItem> mapToOrderItems(List<CreateOrderRequest.OrderItem> items) {
        return items.stream()
                .map(item -> new PaymentInitiatedEvent.OrderItem(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(Collectors.toList());
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .sagaId(order.getSagaId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod().name())
                .transactionId(order.getTransactionId())
                .cancellationReason(order.getCancellationReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
