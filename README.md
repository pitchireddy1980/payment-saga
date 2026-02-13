# Payment Saga Pattern - Spring Boot + Kafka

## Overview

A production-ready implementation of the **Saga Pattern** for distributed payment processing using:
- **Spring Boot 3.2+**
- **Spring Kafka**
- **Spring Data JPA**
- **PostgreSQL**
- **Redis** (for idempotency)
- **Apache Kafka**

## Architecture

### Microservices
1. **Order Service** (Port 8081) - Saga initiator and coordinator
2. **Risk Service** (Port 8082) - Fraud detection and risk assessment
3. **Payment Service** (Port 8083) - Payment processing and refunds
4. **Notification Service** (Port 8084) - Multi-channel notifications

### Technology Stack
- Java 17+
- Spring Boot 3.2.x
- Spring Kafka 3.1.x
- Spring Data JPA
- PostgreSQL 15
- Redis 7
- Lombok
- MapStruct
- Micrometer (metrics)

## Features

✅ **Event-Driven Saga** - Choreography-based pattern  
✅ **Compensating Transactions** - Automatic rollback on failures  
✅ **Timeout Handling** - Configurable timeouts with retry  
✅ **Retry Logic** - Exponential backoff strategy  
✅ **Idempotency** - Duplicate event detection  
✅ **Dead Letter Queue** - Failed message handling  
✅ **Distributed Tracing** - Correlation IDs across services  
✅ **Health Checks** - Spring Actuator endpoints  
✅ **API Documentation** - SpringDoc OpenAPI  

## Project Structure

```
payment-saga-spring/
├── common/
│   ├── events/                 # Shared event DTOs
│   ├── kafka/                  # Kafka configuration
│   └── domain/                 # Shared domain models
├── order-service/
│   ├── src/main/java/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── kafka/
│   │   └── config/
│   └── src/main/resources/
├── risk-service/
├── payment-service/
├── notification-service/
└── docker-compose.yml
```

## Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build all services
mvn clean install

# 3. Start services
# Terminal 1
cd order-service && mvn spring-boot:run

# Terminal 2
cd risk-service && mvn spring-boot:run

# Terminal 3
cd payment-service && mvn spring-boot:run

# Terminal 4
cd notification-service && mvn spring-boot:run
```

## API Usage

```bash
# Initiate payment
curl -X POST http://localhost:8081/api/v1/orders/payment \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "amount": 99.99,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "items": [
      {"productId": "prod-1", "quantity": 2, "price": 49.99}
    ]
  }'

# Check order status
curl http://localhost:8081/api/v1/orders/{orderId}

# Health check
curl http://localhost:8081/actuator/health
```

## Event Flow

**Success Path:**
```
Client → POST /api/v1/orders/payment
  ↓
Order Service → PaymentInitiatedEvent
  ↓
Risk Service → RiskCheckCompletedEvent
  ↓
Payment Service → PaymentProcessedEvent
  ↓
Notification Service → Send Email
  ↓
Order Service → Order Confirmed
```

**Failure Path with Compensation:**
```
Payment Service → PaymentFailedEvent
  ↓
Order Service → OrderCancelledEvent (compensation)
  ↓
Risk Service → Rollback Risk Assessment
  ↓
Payment Service → Refund if needed
  ↓
Notification Service → Send Failure Notification
```

## Configuration

Each service has `application.yml` with:
- Kafka broker configuration
- Database connection
- Redis configuration
- Retry and timeout settings
- Logging configuration

## Monitoring

- **Kafka UI**: http://localhost:8080
- **Order Service Swagger**: http://localhost:8081/swagger-ui.html
- **Actuator Endpoints**: http://localhost:808x/actuator

## Documentation

See `/docs` folder for:
- Event flow diagrams
- Architecture details
- Testing strategies
- Deployment guides
