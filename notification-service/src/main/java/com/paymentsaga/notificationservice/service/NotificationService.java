package com.paymentsaga.notificationservice.service;

import com.paymentsaga.common.events.*;
import com.paymentsaga.common.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Notification Service - Multi-channel notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final KafkaEventPublisher eventPublisher;
    private final EmailService emailService;
    private final SmsService smsService;
    
    private final Set<String> sentNotifications = new HashSet<>();
    private static final String NOTIFICATION_EVENTS_TOPIC = "notification-events";

    /**
     * Send success notification
     */
    public void sendSuccessNotification(PaymentProcessedEvent event) {
        String notificationKey = event.getPayload().getOrderId() + "-SUCCESS";
        
        // Idempotency check
        if (sentNotifications.contains(notificationKey)) {
            log.info("Success notification already sent for order: {}", event.getPayload().getOrderId());
            return;
        }

        log.info("Sending success notification for order: {}", event.getPayload().getOrderId());

        String message = String.format(
                "Your payment of %s %s has been processed successfully. Transaction ID: %s",
                event.getPayload().getAmount(),
                event.getPayload().getCurrency(),
                event.getPayload().getTransactionId()
        );

        sendNotification("user@example.com", "Payment Successful", message, notificationKey);
    }

    /**
     * Send failure notification
     */
    public void sendFailureNotification(PaymentFailedEvent event) {
        String notificationKey = event.getPayload().getOrderId() + "-FAILURE";
        
        if (sentNotifications.contains(notificationKey)) {
            log.info("Failure notification already sent for order: {}", event.getPayload().getOrderId());
            return;
        }

        log.info("Sending failure notification for order: {}", event.getPayload().getOrderId());

        String message = String.format(
                "Your payment failed. Reason: %s. Please try again or contact support.",
                event.getPayload().getReason()
        );

        sendNotification("user@example.com", "Payment Failed", message, notificationKey);
    }

    /**
     * Send cancellation notification
     */
    public void sendCancellationNotification(OrderCancelledEvent event) {
        String notificationKey = event.getPayload().getOrderId() + "-CANCELLED";
        
        if (sentNotifications.contains(notificationKey)) {
            return;
        }

        log.info("Sending cancellation notification for order: {}", event.getPayload().getOrderId());

        String message = String.format(
                "Your order has been cancelled. Reason: %s",
                event.getPayload().getReason()
        );

        sendNotification("user@example.com", "Order Cancelled", message, notificationKey);
    }

    /**
     * Send refund notification
     */
    public void sendRefundNotification(PaymentRefundedEvent event) {
        String notificationKey = event.getPayload().getOrderId() + "-REFUND";
        
        if (sentNotifications.contains(notificationKey)) {
            return;
        }

        log.info("Sending refund notification for order: {}", event.getPayload().getOrderId());

        String message = String.format(
                "Your payment has been refunded. Amount: %s. Refund ID: %s. Reason: %s",
                event.getPayload().getAmount(),
                event.getPayload().getRefundId(),
                event.getPayload().getReason()
        );

        sendNotification("user@example.com", "Payment Refunded", message, notificationKey);
    }

    private void sendNotification(String recipient, String subject, String message, String notificationKey) {
        try {
            // Send via email
            emailService.sendEmail(recipient, subject, message);
            
            // Optionally send SMS
            // smsService.sendSms(phoneNumber, message);

            sentNotifications.add(notificationKey);
            log.info("Notification sent successfully: {}", notificationKey);

        } catch (Exception e) {
            log.error("Failed to send notification: {}", notificationKey, e);
        }
    }
}

/**
 * Email Service (simulated)
 */
@Service
@Slf4j
class EmailService {
    public void sendEmail(String to, String subject, String body) {
        log.info("[EMAIL] To: {}", to);
        log.info("[EMAIL] Subject: {}", subject);
        log.info("[EMAIL] Body: {}", body);
        
        // Simulate email sending
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * SMS Service (simulated)
 */
@Service
@Slf4j
class SmsService {
    public void sendSms(String phoneNumber, String message) {
        log.info("[SMS] To: {}", phoneNumber);
        log.info("[SMS] Message: {}", message);
        
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
