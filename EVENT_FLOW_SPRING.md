# Event Flow Diagrams - Spring Boot + Kafka

## Success Flow

```
┌─────────────┐
│   Client    │
│  (curl/UI)  │
└──────┬──────┘
       │ POST /api/v1/orders/payment
       ▼
┌──────────────────────────────────────┐
│      Order Service (Port 8081)       │
│  @PostMapping("/api/v1/orders/...")  │
│                                      │
│  1. Create Order (PENDING)           │
│  2. Save to PostgreSQL               │
│  3. Publish PaymentInitiatedEvent    │
└──────────┬───────────────────────────┘
           │
           ▼ topic: payment-saga
    ┌──────────────┐
    │    Kafka     │
    └──────┬───────┘
           │
           ▼ @KafkaListener
┌──────────────────────────────────────┐
│      Risk Service (Port 8082)        │
│  RiskEventListener.java              │
│                                      │
│  1. Perform fraud check              │
│  2. Check blacklist                  │
│  3. Velocity check                   │
│  4. Calculate risk score             │
│  5. Save RiskAssessment              │
│  6. Publish RiskCheckCompletedEvent  │
└──────────┬───────────────────────────┘
           │
           ▼ topic: risk-events
    ┌──────────────┐
    │    Kafka     │
    └──────┬───────┘
           │
           ├────────────────────┐
           │                    │
           ▼                    ▼
    ┌─────────────┐    ┌─────────────┐
    │Order Service│    │Payment Svc  │
    │             │    │Port 8083    │
    │Update to    │    │             │
    │PROCESSING   │    │@KafkaListen │
    └─────────────┘    └──────┬──────┘
                              │
                              │ 1. Create PaymentTransaction
                              │ 2. Call Payment Gateway
                              │ 3. Save transaction (COMPLETED)
                              │ 4. Publish PaymentProcessedEvent
                              ▼
                       ┌──────────────┐
                       │    Kafka     │
                       └──────┬───────┘
                              │ topic: payment-events
                              │
                              ├─────────────────┐
                              │                 │
                              ▼                 ▼
                       ┌─────────────┐  ┌──────────────┐
                       │Order Service│  │Notification  │
                       │             │  │Service       │
                       │Confirm Order│  │Port 8084     │
                       │Set txn ID   │  │              │
                       │CONFIRMED    │  │Send Email    │
                       └─────────────┘  └──────────────┘
                                               │
                                               ▼
                                        [EMAIL SENT]
                                        [SAGA COMPLETE]
```

## Failure Flow with Compensation

```
Client
  │
  ▼ POST /api/v1/orders/payment
Order Service
  │ Create order (PENDING)
  │ Publish PaymentInitiatedEvent
  ▼
Risk Service
  │ Perform checks
  │ Risk score > 50 (FAILED)
  │ Publish RiskCheckFailedEvent
  ▼
Kafka (risk-events)
  │
  ▼ @KafkaListener
Order Service
  │
  │ COMPENSATION:
  │ 1. Update order status → CANCELLED
  │ 2. Save cancellation reason
  │ 3. Publish OrderCancelledEvent
  │
  ▼ topic: saga-compensation
Kafka
  │
  ├─────────────────────┐
  │                     │
  ▼                     ▼
Risk Service      Notification Service
  │                     │
  │ ROLLBACK:           │ Send failure email
  │ Set rolledBack=true │
  │ Publish rollback    ▼
  │ event          [EMAIL SENT]
  ▼
[COMPENSATION COMPLETE]
```

## Payment Failure with Refund

```
Order Service → PaymentInitiatedEvent
  ↓
Risk Service → RiskCheckCompletedEvent (approved)
  ↓
Payment Service
  │ Attempt 1: FAIL (gateway timeout)
  │ Wait 2s...
  │ Attempt 2: FAIL
  │ Wait 4s...
  │ Attempt 3: FAIL
  │ Max retries reached
  │
  │ Publish PaymentFailedEvent
  ↓
Kafka (payment-events)
  ↓
Order Service
  │ COMPENSATION:
  │ Publish OrderCancelledEvent
  ↓
Kafka (saga-compensation)
  │
  ├─────────────────┬─────────────────┐
  │                 │                 │
  ▼                 ▼                 ▼
Risk Service   Payment Service  Notification Svc
  │                 │                 │
  │ Rollback        │ (No refund      │ Send failure
  │ risk check      │  needed - not   │ email
  │                 │  completed)     │
  ▼                 ▼                 ▼
[COMPENSATED]  [SKIPPED]        [EMAIL SENT]
```

## Spring Boot Components

### 1. REST Controller (Order Service)

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    
    @PostMapping("/payment")
    public ResponseEntity<OrderResponse> initiatePayment(
            @RequestBody CreateOrderRequest request) {
        // 1. Validate request
        // 2. Call service
        OrderResponse response = orderService.initiatePayment(request);
        return ResponseEntity.status(CREATED).body(response);
    }
}
```

### 2. Service Layer

```java
@Service
@Transactional
public class OrderService {
    
    public OrderResponse initiatePayment(CreateOrderRequest request) {
        // 1. Create order entity
        Order order = Order.builder()
            .userId(request.getUserId())
            .sagaId(UUID.randomUUID().toString())
            .status(OrderStatus.PENDING)
            .build();
        
        // 2. Save to database
        orderRepository.save(order);
        
        // 3. Publish event
        PaymentInitiatedEvent event = createEvent(order);
        eventPublisher.publishEvent("payment-saga", event);
        
        return mapToResponse(order);
    }
}
```

### 3. Kafka Listener

```java
@Component
public class OrderEventListener {
    
    @KafkaListener(
        topics = "risk-events",
        groupId = "order-service-group"
    )
    public void handleRiskEvent(
            @Payload String payload,
            Acknowledgment ack) {
        
        BaseEvent event = deserialize(payload);
        
        if (event.getEventType() == RISK_CHECK_COMPLETED) {
            // Process success
            orderService.updateToProcessing(event.getSagaId());
        } else {
            // Start compensation
            orderService.cancelOrder(event.getSagaId());
        }
        
        ack.acknowledge();
    }
}
```

### 4. JPA Entity

```java
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String sagaId;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    private BigDecimal amount;
    
    // ... other fields
}
```

### 5. Event Publisher

```java
@Component
public class KafkaEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishEvent(String topic, BaseEvent event) {
        Message<BaseEvent> message = MessageBuilder
            .withPayload(event)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .setHeader(KafkaHeaders.KEY, event.getSagaId())
            .build();
        
        kafkaTemplate.send(message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish", ex);
                } else {
                    log.info("Published: {}", event.getEventType());
                }
            });
    }
}
```

## Configuration Highlights

### application.yml (Order Service)

```yaml
spring:
  application:
    name: order-service
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: order-service-group
      enable-auto-commit: false
    producer:
      acks: all
      retries: 3
  datasource:
    url: jdbc:postgresql://localhost:5432/orders
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: 8081

saga:
  timeout-ms: 15000
  max-retries: 3
```

### Kafka Configuration

```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ACKS_CONFIG, "all");
        config.put(RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
```

## Testing with cURL

```bash
# Success scenario
curl -X POST http://localhost:8081/api/v1/orders/payment \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "amount": 99.99,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "items": [{"productId": "p1", "quantity": 2, "price": 49.99}]
  }'

# Response:
{
  "orderId": "abc-123",
  "sagaId": "xyz-789",
  "status": "PENDING",
  ...
}

# Check status
curl http://localhost:8081/api/v1/orders/abc-123 \
  -H "X-User-Id: user-123"

# Response after completion:
{
  "orderId": "abc-123",
  "status": "CONFIRMED",
  "transactionId": "txn-456",
  ...
}
```

## Key Annotations

```java
@SpringBootApplication    // Main application class
@EnableKafka             // Enable Kafka support
@EnableJpaAuditing       // Enable JPA auditing
@RestController          // REST endpoint
@Service                 // Service layer
@Component               // Spring-managed bean
@Entity                  // JPA entity
@KafkaListener           // Kafka consumer
@Transactional           // Transaction boundary
@Retryable              // Retry logic
```

## Architecture Benefits

✅ **Spring Boot Integration**: Native Spring ecosystem  
✅ **Auto-configuration**: Minimal boilerplate  
✅ **Dependency Injection**: Clean, testable code  
✅ **JPA/Hibernate**: Easy database access  
✅ **Spring Kafka**: Robust event handling  
✅ **Actuator**: Built-in monitoring  
✅ **Validation**: Jakarta Bean Validation  
✅ **OpenAPI**: Auto-generated API docs  
