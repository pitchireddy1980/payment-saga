# Payment Saga - Spring Boot + Kafka Setup Guide

## Prerequisites

- **Java 17+** (JDK)
- **Maven 3.8+**
- **Docker & Docker Compose**
- **IDE** (IntelliJ IDEA recommended)

## Project Structure

```
payment-saga-spring/
├── common/                          # Shared events and Kafka configuration
│   └── src/main/java/
│       └── com/paymentsaga/common/
│           ├── events/              # Event DTOs
│           └── kafka/               # Kafka config, publisher, error handler
├── order-service/                   # Port 8081
│   └── src/main/java/
│       └── com/paymentsaga/orderservice/
│           ├── controller/          # REST endpoints
│           ├── service/             # Business logic
│           ├── entity/              # JPA entities
│           ├── repository/          # Data access
│           └── kafka/               # Event listeners
├── risk-service/                    # Port 8082
├── payment-service/                 # Port 8083
├── notification-service/            # Port 8084
├── pom.xml                          # Parent POM
└── docker-compose.yml               # Infrastructure
```

## Installation & Setup

### 1. Clone/Extract Project

```bash
unzip payment-saga-spring.zip
cd payment-saga-spring
```

### 2. Build All Services

```bash
# Build the entire project
mvn clean install

# Or build individual services
cd order-service && mvn clean package
cd ../risk-service && mvn clean package
cd ../payment-service && mvn clean package
cd ../notification-service && mvn clean package
```

### 3. Start Infrastructure

```bash
# Start Kafka, PostgreSQL, Redis
docker-compose up -d zookeeper kafka postgres-order postgres-risk postgres-payment redis kafka-ui

# Check services are running
docker-compose ps

# View logs
docker-compose logs -f kafka
```

### 4. Create Kafka Topics (Optional - auto-created)

```bash
docker exec -it kafka bash

# Inside container
kafka-topics --create --topic payment-saga --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic risk-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic payment-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic saga-compensation --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic notification-events --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
kafka-topics --create --topic payment-saga-dlq --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

# List topics
kafka-topics --list --bootstrap-server localhost:9092

exit
```

## Running the Services

### Option 1: Run with Maven (Development)

```bash
# Terminal 1 - Order Service
cd order-service
mvn spring-boot:run

# Terminal 2 - Risk Service
cd risk-service
mvn spring-boot:run

# Terminal 3 - Payment Service
cd payment-service
mvn spring-boot:run

# Terminal 4 - Notification Service
cd notification-service
mvn spring-boot:run
```

### Option 2: Run with Docker Compose (Production-like)

```bash
# Build and start all services
docker-compose up --build

# Or start in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f order-service
docker-compose logs -f risk-service
docker-compose logs -f payment-service
docker-compose logs -f notification-service
```

### Option 3: Run JARs

```bash
# Build JARs
mvn clean package

# Run services
java -jar order-service/target/order-service-1.0.0.jar
java -jar risk-service/target/risk-service-1.0.0.jar
java -jar payment-service/target/payment-service-1.0.0.jar
java -jar notification-service/target/notification-service-1.0.0.jar
```

## Testing the Saga

### 1. Initiate a Successful Payment

```bash
curl -X POST http://localhost:8081/api/v1/orders/payment \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "amount": 99.99,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "items": [
      {
        "productId": "prod-1",
        "quantity": 2,
        "price": 49.99
      }
    ]
  }'
```

**Expected Response:**
```json
{
  "orderId": "uuid-here",
  "userId": "user-123",
  "sagaId": "uuid-here",
  "amount": 99.99,
  "currency": "USD",
  "status": "PENDING",
  "paymentMethod": "CREDIT_CARD",
  "createdAt": "2024-...",
  "updatedAt": "2024-..."
}
```

**Event Flow:**
1. `PaymentInitiatedEvent` → payment-saga topic
2. `RiskCheckCompletedEvent` → risk-events topic
3. `PaymentProcessedEvent` → payment-events topic
4. Email notification sent
5. Order status → CONFIRMED

### 2. Test Risk Check Failure (Blocked User)

```bash
curl -X POST http://localhost:8081/api/v1/orders/payment \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "blocked-user-456",
    "amount": 149.99,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "items": [{"productId": "prod-2", "quantity": 1, "price": 149.99}]
  }'
```

**Expected Flow:**
1. `PaymentInitiatedEvent`
2. `RiskCheckFailedEvent` (blacklist hit)
3. `OrderCancelledEvent` (compensation)
4. Email notification sent (failure)
5. Order status → CANCELLED

### 3. Test Fraud Detection (High Amount)

```bash
curl -X POST http://localhost:8081/api/v1/orders/payment \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-789",
    "amount": 15000.00,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "items": [{"productId": "prod-3", "quantity": 1, "price": 15000}]
  }'
```

**Expected:** Risk check fails due to high amount (>$10,000)

### 4. Get Order Status

```bash
curl -X GET http://localhost:8081/api/v1/orders/{orderId} \
  -H "X-User-Id: user-123"
```

## Monitoring & Observability

### Kafka UI
- **URL:** http://localhost:8080
- **Features:**
  - View topics and messages
  - Monitor consumer groups
  - Check lag and throughput
  - Inspect Dead Letter Queue

### Swagger UI / OpenAPI

- **Order Service:** http://localhost:8081/swagger-ui.html
- **Risk Service:** http://localhost:8082/swagger-ui.html
- **Payment Service:** http://localhost:8083/swagger-ui.html
- **Notification Service:** http://localhost:8084/swagger-ui.html

### Actuator Health Checks

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

### Metrics (Prometheus)

```bash
curl http://localhost:8081/actuator/prometheus
```

### View Kafka Messages

```bash
# Consume from specific topic
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment-saga \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true

# View DLQ messages
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment-saga-dlq \
  --from-beginning
```

### Consumer Group Status

```bash
# List consumer groups
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list

# Check lag
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --describe
```

## Configuration

### Application Properties

Each service has `application.yml` in `src/main/resources/`:

```yaml
spring:
  application:
    name: service-name
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: service-name-group
  datasource:
    url: jdbc:postgresql://localhost:5432/dbname
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: 808X

saga:
  timeout-ms: 15000
  max-retries: 3
```

### Environment Variables

Override via environment variables:

```bash
export SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
export SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/db
export SPRING_DATASOURCE_USERNAME=user
export SPRING_DATASOURCE_PASSWORD=pass
```

## Troubleshooting

### Issue: Services can't connect to Kafka

**Solution:**
```bash
# Check Kafka is running
docker ps | grep kafka

# Check Kafka logs
docker logs kafka

# Test connection
docker exec -it kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092
```

### Issue: Database connection failed

**Solution:**
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Test connection
docker exec -it postgres-order psql -U orderuser -d orders
```

### Issue: Events not being consumed

**Solution:**
```bash
# Check consumer lag
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --describe

# Restart consumer
docker-compose restart order-service
```

### Issue: Reset consumer offset (for testing)

```bash
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --reset-offsets --to-earliest \
  --execute --all-topics
```

## Development Tips

### Hot Reload

Use Spring Boot DevTools:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### Debugging

1. Run service in debug mode: `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"`
2. Attach debugger to port 5005

### Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Run specific test
mvn test -Dtest=OrderServiceTest
```

## Production Deployment

### Build Optimized JARs

```bash
mvn clean package -DskipTests -Pproduction
```

### Docker Images

```bash
# Build images
docker-compose build

# Push to registry
docker tag order-service:latest myregistry/order-service:1.0.0
docker push myregistry/order-service:1.0.0
```

### Kubernetes

See `/k8s` directory for Kubernetes manifests (if included).

## Next Steps

1. ✅ Run the services
2. ✅ Test with curl or Postman
3. ✅ Monitor events in Kafka UI
4. ✅ Check service logs
5. ✅ View metrics in Actuator
6. 📊 Add Grafana dashboards
7. 🔍 Add distributed tracing (Zipkin/Jaeger)
8. 🔒 Add authentication/authorization
9. 🚀 Deploy to Kubernetes

## Support

- Logs: `docker-compose logs -f [service-name]`
- Kafka UI: http://localhost:8080
- Swagger: http://localhost:808X/swagger-ui.html
- Actuator: http://localhost:808X/actuator
