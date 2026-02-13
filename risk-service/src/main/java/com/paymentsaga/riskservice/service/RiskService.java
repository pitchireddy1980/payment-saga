package com.paymentsaga.riskservice.service;

import com.paymentsaga.common.events.*;
import com.paymentsaga.common.kafka.KafkaEventPublisher;
import com.paymentsaga.riskservice.entity.RiskAssessment;
import com.paymentsaga.riskservice.repository.RiskAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Risk Service - Fraud Detection and Risk Assessment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final KafkaEventPublisher eventPublisher;
    
    private static final String RISK_EVENTS_TOPIC = "risk-events";
    private static final String SAGA_COMPENSATION_TOPIC = "saga-compensation";
    private static final int RISK_THRESHOLD = 50; // Risk score threshold

    /**
     * Perform risk assessment on payment
     */
    @Transactional
    public void performRiskAssessment(PaymentInitiatedEvent event) {
        String orderId = event.getPayload().getOrderId();
        String userId = event.getPayload().getUserId();
        
        log.info("Starting risk assessment for order: {}", orderId);

        try {
            // Perform fraud checks
            boolean fraudCheck = checkFraud(userId, event.getPayload().getAmount());
            boolean velocityCheck = checkVelocity(userId);
            boolean blacklistCheck = checkBlacklist(userId, event.getPayload().getPaymentMethod());

            // Calculate risk score
            int riskScore = calculateRiskScore(fraudCheck, velocityCheck, blacklistCheck);
            boolean approved = riskScore < RISK_THRESHOLD;

            // Save risk assessment
            RiskAssessment assessment = RiskAssessment.builder()
                    .orderId(orderId)
                    .sagaId(event.getSagaId())
                    .userId(userId)
                    .riskScore(riskScore)
                    .approved(approved)
                    .fraudCheck(fraudCheck)
                    .velocityCheck(velocityCheck)
                    .blacklistCheck(blacklistCheck)
                    .rolledBack(false)
                    .build();

            riskAssessmentRepository.save(assessment);

            // Publish result event
            if (approved) {
                publishRiskCheckCompleted(event, assessment);
            } else {
                publishRiskCheckFailed(event, assessment);
            }

        } catch (Exception e) {
            log.error("Risk assessment failed for order: {}", orderId, e);
            publishRiskCheckFailed(event, null);
        }
    }

    /**
     * Rollback risk assessment - Compensating transaction
     */
    @Transactional
    public void rollbackRiskCheck(String sagaId) {
        riskAssessmentRepository.findBySagaId(sagaId)
                .ifPresent(assessment -> {
                    if (!assessment.getRolledBack()) {
                        assessment.setRolledBack(true);
                        riskAssessmentRepository.save(assessment);
                        
                        log.info("Risk assessment rolled back for saga: {}", sagaId);
                        
                        // Publish rollback event
                        publishRiskCheckRollback(sagaId, assessment.getOrderId());
                    }
                });
    }

    private boolean checkFraud(String userId, java.math.BigDecimal amount) {
        // Fraud detection logic
        // For demo: reject amounts over $10,000
        if (amount.compareTo(new java.math.BigDecimal("10000")) > 0) {
            log.warn("Fraud detected: Amount too high for user {}", userId);
            return false;
        }
        return true;
    }

    private boolean checkVelocity(String userId) {
        // Velocity check - too many transactions in short time
        // For demo: always pass
        return true;
    }

    private boolean checkBlacklist(String userId, String paymentMethod) {
        // Blacklist check
        // For demo: reject users with "blocked" in ID
        if (userId.toLowerCase().contains("blocked")) {
            log.warn("Blacklist hit for user: {}", userId);
            return false;
        }
        return true;
    }

    private int calculateRiskScore(boolean fraudCheck, boolean velocityCheck, boolean blacklistCheck) {
        int score = 0;
        if (!fraudCheck) score += 40;
        if (!velocityCheck) score += 30;
        if (!blacklistCheck) score += 30;
        return score;
    }

    private void publishRiskCheckCompleted(PaymentInitiatedEvent originalEvent, RiskAssessment assessment) {
        RiskCheckCompletedEvent event = RiskCheckCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.RISK_CHECK_COMPLETED)
                .timestamp(LocalDateTime.now())
                .sagaId(originalEvent.getSagaId())
                .correlationId(originalEvent.getCorrelationId())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new RiskCheckCompletedEvent.RiskCheckCompletedPayload(
                        assessment.getOrderId(),
                        assessment.getRiskScore(),
                        assessment.getApproved(),
                        new RiskCheckCompletedEvent.RiskChecks(
                                assessment.getFraudCheck(),
                                assessment.getVelocityCheck(),
                                assessment.getBlacklistCheck())))
                .build();

        eventPublisher.publishEvent(RISK_EVENTS_TOPIC, event);
        log.info("Risk check completed for order: {}, approved: {}", assessment.getOrderId(), assessment.getApproved());
    }

    private void publishRiskCheckFailed(PaymentInitiatedEvent originalEvent, RiskAssessment assessment) {
        RiskCheckFailedEvent event = RiskCheckFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.RISK_CHECK_FAILED)
                .timestamp(LocalDateTime.now())
                .sagaId(originalEvent.getSagaId())
                .correlationId(originalEvent.getCorrelationId())
                .version("1.0")
                .metadata(createMetadata())
                .payload(new RiskCheckFailedEvent.RiskCheckFailedPayload(
                        originalEvent.getPayload().getOrderId(),
                        "High risk score detected",
                        assessment != null ? assessment.getRiskScore() : 100))
                .build();

        eventPublisher.publishEvent(RISK_EVENTS_TOPIC, event);
        log.warn("Risk check failed for order: {}", originalEvent.getPayload().getOrderId());
    }

    private void publishRiskCheckRollback(String sagaId, String orderId) {
        BaseEvent event = BaseEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.RISK_CHECK_ROLLBACK)
                .timestamp(LocalDateTime.now())
                .sagaId(sagaId)
                .correlationId(UUID.randomUUID().toString())
                .version("1.0")
                .metadata(createMetadata())
                .build();

        eventPublisher.publishEvent(SAGA_COMPENSATION_TOPIC, event);
    }

    private BaseEvent.EventMetadata createMetadata() {
        BaseEvent.EventMetadata metadata = new BaseEvent.EventMetadata();
        metadata.setRetryCount(0);
        metadata.setMaxRetries(3);
        metadata.setTimeoutMs(15000L);
        metadata.setSource("risk-service");
        return metadata;
    }
}
