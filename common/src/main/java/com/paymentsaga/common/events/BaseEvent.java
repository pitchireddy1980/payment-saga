package com.paymentsaga.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base event class for all saga events
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    
    private String eventId;
    private EventType eventType;
    private LocalDateTime timestamp;
    private String sagaId;
    private String correlationId;
    private String version;
    private EventMetadata metadata;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventMetadata {
        private int retryCount;
        private int maxRetries;
        private long timeoutMs;
        private String source;
        private Map<String, String> additionalData;
    }
}
