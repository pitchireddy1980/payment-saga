package com.paymentsaga.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.stereotype.Component;

/**
 * Custom error handler for Kafka consumers with retry and DLQ support
 */
@Slf4j
@Component
public class KafkaErrorHandler implements CommonErrorHandler {

    private static final String DLQ_TOPIC = "payment-saga-dlq";
    private static final int MAX_RETRIES = 3;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ExponentialBackOffWithMaxRetries backOff;

    public KafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        
        // Configure exponential backoff: 2s, 4s, 8s
        this.backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        this.backOff.setInitialInterval(2000L);
        this.backOff.setMultiplier(2.0);
        this.backOff.setMaxInterval(30000L);
    }

    @Override
    public boolean handleOne(Exception exception, ConsumerRecord<?, ?> record,
                             org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                             MessageListenerContainer container) {
        
        log.error("Error processing message from topic: {}, partition: {}, offset: {}",
                record.topic(), record.partition(), record.offset(), exception);

        // Get retry count from headers
        int retryCount = getRetryCount(record);
        
        if (retryCount < MAX_RETRIES) {
            log.info("Retrying message (attempt {}/{})", retryCount + 1, MAX_RETRIES);
            
            // Calculate backoff time
            long backoffTime = backOff.backOff(retryCount).backOffPeriod();
            
            try {
                Thread.sleep(backoffTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted during backoff", e);
            }
            
            // Return false to retry
            return false;
            
        } else {
            log.error("Max retries exceeded. Sending message to DLQ: {}", DLQ_TOPIC);
            sendToDeadLetterQueue(record, exception);
            
            // Return true to skip this record
            return true;
        }
    }

    private int getRetryCount(ConsumerRecord<?, ?> record) {
        // Get retry count from Kafka headers
        var retryHeader = record.headers().lastHeader("retry-count");
        if (retryHeader != null) {
            return Integer.parseInt(new String(retryHeader.value()));
        }
        return 0;
    }

    private void sendToDeadLetterQueue(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            DeadLetterMessage dlqMessage = DeadLetterMessage.builder()
                    .originalTopic(record.topic())
                    .partition(record.partition())
                    .offset(record.offset())
                    .key(record.key() != null ? record.key().toString() : null)
                    .value(record.value())
                    .exception(exception.getMessage())
                    .stackTrace(getStackTrace(exception))
                    .timestamp(System.currentTimeMillis())
                    .build();

            kafkaTemplate.send(DLQ_TOPIC, record.key() != null ? record.key().toString() : null, dlqMessage);
            
            log.info("Message sent to DLQ successfully");
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ", e);
        }
    }

    private String getStackTrace(Exception exception) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class DeadLetterMessage {
        private String originalTopic;
        private Integer partition;
        private Long offset;
        private String key;
        private Object value;
        private String exception;
        private String stackTrace;
        private Long timestamp;
    }
}
