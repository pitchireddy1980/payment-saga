package com.paymentsaga.common.kafka;

import com.paymentsaga.common.events.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka event publisher with timeout and retry support
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish event to Kafka topic
     */
    public CompletableFuture<SendResult<String, Object>> publishEvent(String topic, BaseEvent event) {
        log.info("Publishing event {} to topic {}", event.getEventType(), topic);

        // Enrich event if needed
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        if (event.getCorrelationId() == null) {
            event.setCorrelationId(UUID.randomUUID().toString());
        }

        // Build message with headers
        Message<BaseEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, event.getSagaId())
                .setHeader("eventType", event.getEventType().getValue())
                .setHeader("eventId", event.getEventId())
                .setHeader("correlationId", event.getCorrelationId())
                .setHeader("timestamp", event.getTimestamp().toString())
                .build();

        // Send message
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);

        // Add callbacks
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Event published successfully: {} to topic: {} at offset: {}",
                        event.getEventType(),
                        topic,
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish event: {} to topic: {}",
                        event.getEventType(), topic, ex);
            }
        });

        return future;
    }

    /**
     * Publish event synchronously
     */
    public void publishEventSync(String topic, BaseEvent event) {
        try {
            publishEvent(topic, event).get();
        } catch (Exception e) {
            log.error("Error publishing event synchronously", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
