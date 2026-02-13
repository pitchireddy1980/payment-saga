package com.paymentsaga.riskservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentsaga.common.events.BaseEvent;
import com.paymentsaga.common.events.EventType;
import com.paymentsaga.common.events.PaymentInitiatedEvent;
import com.paymentsaga.riskservice.service.RiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka event listeners for Risk Service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventListener {

    private final RiskService riskService;
    private final ObjectMapper objectMapper;

    /**
     * Listen to Payment Initiated events
     */
    @KafkaListener(
        topics = "payment-saga",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentInitiated(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received payment saga event with key: {}", key);
            
            BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
            
            if (event.getEventType() == EventType.PAYMENT_INITIATED) {
                PaymentInitiatedEvent paymentEvent = objectMapper.convertValue(event, PaymentInitiatedEvent.class);
                riskService.performRiskAssessment(paymentEvent);
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment initiated event", e);
            throw new RuntimeException("Failed to process event", e);
        }
    }

    /**
     * Listen to compensation events
     */
    @KafkaListener(
        topics = "saga-compensation",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleCompensation(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received compensation event with key: {}", key);
            
            BaseEvent event = objectMapper.readValue(payload, BaseEvent.class);
            
            // Rollback risk assessment on order cancellation or payment failure
            if (event.getEventType() == EventType.ORDER_CANCELLED ||
                event.getEventType() == EventType.PAYMENT_FAILED) {
                riskService.rollbackRiskCheck(event.getSagaId());
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing compensation event", e);
            throw new RuntimeException("Failed to process compensation", e);
        }
    }
}
