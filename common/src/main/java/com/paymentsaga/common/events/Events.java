package com.paymentsaga.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment Initiated Event - Starts the saga
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentInitiatedEvent extends BaseEvent {
    private PaymentInitiatedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInitiatedPayload {
        private String orderId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private List<OrderItem> items;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private Integer quantity;
        private BigDecimal price;
    }
}

/**
 * Risk Check Completed Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class RiskCheckCompletedEvent extends BaseEvent {
    private RiskCheckCompletedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCheckCompletedPayload {
        private String orderId;
        private Integer riskScore;
        private Boolean approved;
        private RiskChecks checks;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskChecks {
        private Boolean fraudCheck;
        private Boolean velocityCheck;
        private Boolean blacklistCheck;
    }
}

/**
 * Risk Check Failed Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class RiskCheckFailedEvent extends BaseEvent {
    private RiskCheckFailedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskCheckFailedPayload {
        private String orderId;
        private String reason;
        private Integer riskScore;
    }
}

/**
 * Payment Processed Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class PaymentProcessedEvent extends BaseEvent {
    private PaymentProcessedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentProcessedPayload {
        private String orderId;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime processedAt;
    }
}

/**
 * Payment Failed Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class PaymentFailedEvent extends BaseEvent {
    private PaymentFailedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailedPayload {
        private String orderId;
        private String reason;
        private String errorCode;
    }
}

/**
 * Payment Refunded Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class PaymentRefundedEvent extends BaseEvent {
    private PaymentRefundedPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRefundedPayload {
        private String orderId;
        private String transactionId;
        private String refundId;
        private BigDecimal amount;
        private String reason;
    }
}

/**
 * Order Cancelled Event (Compensation)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class OrderCancelledEvent extends BaseEvent {
    private OrderCancelledPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCancelledPayload {
        private String orderId;
        private String reason;
        private LocalDateTime cancelledAt;
    }
}

/**
 * Notification Sent Event
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
class NotificationSentEvent extends BaseEvent {
    private NotificationSentPayload payload;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSentPayload {
        private String orderId;
        private String userId;
        private NotificationChannel channel;
        private NotificationType type;
    }
    
    public enum NotificationChannel {
        EMAIL, SMS, PUSH
    }
    
    public enum NotificationType {
        SUCCESS, FAILURE, REFUND
    }
}
