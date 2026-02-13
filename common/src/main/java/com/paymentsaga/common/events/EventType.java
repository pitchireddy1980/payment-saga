package com.paymentsaga.common.events;

/**
 * Event types used in the payment saga
 */
public enum EventType {
    // Order Events
    PAYMENT_INITIATED("payment.initiated"),
    ORDER_CONFIRMED("order.confirmed"),
    ORDER_CANCELLED("order.cancelled"),
    
    // Risk Events
    RISK_CHECK_STARTED("risk.check.started"),
    RISK_CHECK_COMPLETED("risk.check.completed"),
    RISK_CHECK_FAILED("risk.check.failed"),
    RISK_CHECK_ROLLBACK("risk.check.rollback"),
    
    // Payment Events
    PAYMENT_PROCESSING("payment.processing"),
    PAYMENT_PROCESSED("payment.processed"),
    PAYMENT_FAILED("payment.failed"),
    PAYMENT_REFUNDED("payment.refunded"),
    
    // Notification Events
    NOTIFICATION_SENT("notification.sent"),
    NOTIFICATION_FAILED("notification.failed"),
    
    // Saga Events
    SAGA_COMPLETED("saga.completed"),
    SAGA_FAILED("saga.failed"),
    SAGA_TIMEOUT("saga.timeout");
    
    private final String value;
    
    EventType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
