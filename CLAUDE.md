# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Webstore is a microservices-based e-commerce backend application for selling books. This is a **multi-module Gradle
monorepo** with 9 independent microservices.

**Technology Stack:**

- Java 21
- Spring Boot 3.4.4 - 3.5.6
- Spring Cloud 2024.0.1 - 2025.0.0
- PostgreSQL 17
- Apache Kafka 4
- Netflix Eureka (Service Discovery)
- Spring Cloud Config (Centralized Configuration)
- Spring Cloud Gateway MVC (API Gateway)

**Note:** This is backend-only - no UI implementation.

## Build Commands

This is a Gradle-based multi-module project using Gradle 8.10.2.

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :order-service:build
./gradlew :product-service:build

# Run tests for all services
./gradlew test

# Run tests for specific service
./gradlew :cart-service:test

# Clean build
./gradlew clean build

# Run a specific service
./gradlew :order-service:bootRun
./gradlew :gateway-service:bootRun

# Skip tests during build
./gradlew build -x test

# Build without daemon (useful for CI/CD)
./gradlew build --no-daemon
```

## Microservices Architecture

### Service Inventory

The system consists of 9 microservices defined in `settings.gradle`:

| Service               | Port | Purpose                                            | Database   |
|-----------------------|------|----------------------------------------------------|------------|
| **config-service**    | 8071 | Centralized configuration via Spring Cloud Config  | N/A        |
| **discovery-service** | 8761 | Service registry using Netflix Eureka              | N/A        |
| **gateway-service**   | 8080 | API Gateway using Spring Cloud Gateway MVC         | N/A        |
| **product-service**   | 808x | Book catalog with authors, publishers, categories  | PostgreSQL |
| **inventory-service** | 808x | Stock level management and reservation tracking    | PostgreSQL |
| **user-service**      | 808x | User registration, authentication, profiles, roles | PostgreSQL |
| **cart-service**      | 808x | Shopping cart logic per user                       | PostgreSQL |
| **order-service**     | 808x | Order placement and tracking                       | PostgreSQL |
| **payment-service**   | 808x | Payment processing and refunds                     | PostgreSQL |

**Service-Specific Documentation:** Each service has its own `CLAUDE.md` file in its directory with detailed
implementation guidance.

### Startup Order

Services must be started in this order for proper operation:

1. **config-service** (8071) - Required by all other services for configuration
2. **discovery-service** (8761) - Required for service discovery
3. **Infrastructure Services:**
    - PostgreSQL (5432)
    - Kafka Broker
4. **Business Services** (any order):
    - product-service, inventory-service, user-service
    - cart-service, order-service, payment-service
5. **gateway-service** (8080) - API Gateway (routes to other services)

## System Architecture

### Communication Patterns

**1. Synchronous Communication (REST):**

- Services use `RestClient` (Spring Framework) with Eureka service discovery
- Services call each other using service names (e.g., `http://cart-service/v1/...`)
- Load balancing via `@LoadBalanced` RestClient.Builder

**Key Inter-Service REST Calls:**

- Order Service → Cart Service: Fetch cart items during order creation
    - `GET http://cart-service/v1/carts/cart/items/{cartID}`

**2. Asynchronous Communication (Kafka):**

- Event-driven messaging for decoupled operations
- Exactly-once semantics with transactional producers/consumers
- Each service has unique transactional ID based on `{service-name}-tx-{port}`

**Kafka Topics & Event Flows:**

| Topic                | Producer                    | Consumer          | Event Type       | Purpose                   |
|----------------------|-----------------------------|-------------------|------------------|---------------------------|
| `stock-status-topic` | order-service, cart-service | inventory-service | StockStatusKafka | Stock reservation/release |
| `order-status-topic` | payment-service             | order-service     | OrderStatusKafka | Payment status updates    |

**Stock Management Flow:**

1. Order placed → Order Service publishes "reserve" event
2. Inventory Service reserves stock
3. Order cancelled/refunded → "release" event
4. Order delivered → "commit" event (permanent reservation)

**Payment Flow:**

1. Payment processed → Payment Service publishes OrderStatusKafka
2. Order Service updates order status (PROCESSING/PENDING/REFUNDED)

### Configuration Management

**Spring Cloud Config Server (port 8071):**

- Git-backed configuration: https://github.com/otabek-samatov/webstore-config
- All services fetch configuration from Config Server on startup
- Database connections, Kafka topics, and service-specific properties externalized

**Application Properties Pattern:**

- Each service has `application.yml` with minimal bootstrap config:
  ```yaml
  spring:
    application:
      name: {service-name}
    config:
      import: "optional:configserver:"
    cloud:
      config:
        uri: http://localhost:8071
  ```

### Database Architecture

**Database-per-Service Pattern:**

- Each business service has its own PostgreSQL schema
- Single shared PostgreSQL instance (localhost:5432/webstore)
- Flyway migrations in each service: `src/main/resources/db/migration/`

**Schema Overview:**

**Product Service:**

- `author`, `book`, `book_author`, `book_category`, `book_images`, `category`, `publisher`
- Supports multi-author books, hierarchical categories

**Inventory Service:**

- `inventory` with stock levels, reserved stock, version for optimistic locking

**User Service:**

- `users`, `user_profile`, `address`, `security_role`

**Cart Service:**

- `cart`, `cart_item` linked to users

**Order Service:**

- `orders`, `order_item` with status, shipping/tax calculations

**Payment Service:**

- `payment`, `refund` linked to orders

### Common Architectural Patterns

All services follow consistent patterns:

**1. Layered Architecture:**

```
Controllers (@RestController)
    ↓
Managers (Business Logic with @Service)
    ↓
Repositories (Spring Data JPA)
    ↓
Entities (JPA/Hibernate)
    ↓
PostgreSQL Database
```

**2. DTO Pattern:**

- Separate DTOs for API contracts
- MapStruct 1.5.5.Final for entity-to-DTO mapping
- Mappers use `componentModel = "spring"`

**3. Package Structure (per service):**

```
{service}/
├── {ServiceName}Application.java
├── controllers/       # REST endpoints (@RestController)
├── managers/          # Business logic (@Service)
├── repositories/      # Data access (JpaRepository)
├── entities/          # JPA entities
├── dto/              # Data Transfer Objects
│   └── kafka/        # Kafka-specific DTOs
├── mappers/          # MapStruct interfaces
├── configs/          # Spring configurations (KafkaConfig, RestConfig)
├── validators/       # Input validation
└── exceptions/       # Custom exceptions
```

**4. Configuration Classes:**

- `KafkaConfig.java` - Kafka producer/consumer configuration with exactly-once semantics
- `RestConfig.java` - RestClient with Eureka integration

**5. Database Versioning:**

- Flyway for schema migrations
- Optimistic locking with `@Version` fields
- Sequences for ID generation

**6. Transaction Management:**

- `@Transactional` on service/manager methods
- Kafka transactions for exactly-once delivery
- Transactional boundaries at manager layer

## API Gateway & Service Discovery

**API Gateway (Spring Cloud Gateway MVC):**

- Single entry point for all client requests
- Routes to microservices using Eureka service names
- Configured via Config Server

**Service Discovery (Netflix Eureka):**

- All services register with Eureka Server (port 8761)
- Services discover each other by application name
- Enables dynamic load balancing and failover

## Kafka Configuration Details

### Exactly-Once Semantics

All services using Kafka implement exactly-once semantics:

**Producer Configuration:**

- Transactional ID: `{application-name}-tx-{server-port}` (unique per instance)
- `enable.idempotence=true`
- `acks=all` (all replicas must acknowledge)
- `retries=Integer.MAX_VALUE`
- `max.in.flight.requests.per.connection=5`

**Consumer Configuration:**

- Consumer group: `{application-name}-group`
- `isolation.level=read_committed` (only reads committed transactions)
- `enable.auto.commit=false` (manual offset management)
- RECORD-level acknowledgment mode
- `@Transactional` on listener methods

### Multi-Instance Deployment

**CRITICAL:** When running multiple instances of a service:

- Each instance MUST use a different port (e.g., 8081, 8082, 8083)
- This ensures unique Kafka transactional IDs per instance
- Without unique ports, producer fencing will occur (instances fence each other out)

## Development Workflow

### Adding a New Microservice

1. Add service to `settings.gradle`
2. Create service directory with `build.gradle`
3. Follow standard package structure (controllers/managers/repositories/etc.)
4. Configure `application.yml` with service name and Config Server URI
5. Register with Eureka by including `spring-cloud-starter-netflix-eureka-client`
6. Add Flyway migrations in `src/main/resources/db/migration/`
7. Create service-specific `CLAUDE.md`

### Modifying Existing Services

**When adding new features:**

1. Review service-specific `CLAUDE.md` for architectural guidance
2. Follow existing patterns (DTOs, MapStruct, layered architecture)
3. Add Flyway migration if database changes are needed
4. Update Kafka configs if new event types are introduced
5. Use `@Transactional` for consistency

**When adding Kafka event types:**

1. Create DTO in `{service}/dto/kafka/` package
2. Add consumer factory and container factory in `KafkaConfig`
3. Use `@Transactional` on consumer methods
4. Add validation and null checks in handlers

**When modifying database entities:**

1. Create Flyway migration (next version number: `V2__description.sql`)
2. Update entity class
3. Update corresponding DTO
4. Update MapStruct mapper if needed
5. Consider optimistic locking (`@Version`) implications

### Testing

**Test Structure:**

- Unit tests in `src/test/java/{service}/`
- Use JUnit 5 and Mockito
- Spring Boot Test support with `@SpringBootTest`
- Mock external REST calls and Kafka interactions

**Running Tests:**

```bash
# All services
./gradlew test

# Specific service
./gradlew :order-service:test

# With coverage
./gradlew test jacocoTestReport
```

## Common Pitfalls & Troubleshooting

**1. Service Won't Start:**

- Ensure Config Server is running first (port 8071)
- Ensure Eureka Server is running (port 8761)
- Check Config Server can reach Git repository
- Verify PostgreSQL is running and accessible

**2. Kafka Issues:**

- Ensure Kafka broker is running
- Check transactional ID uniqueness (different ports per instance)
- Verify topic creation with correct partitions/replication
- Check consumer group configuration

**3. REST Call Failures:**

- Verify target service is registered with Eureka
- Check service name matches Eureka application name
- Ensure `@LoadBalanced` is used on RestClient.Builder

**4. Database Migration Errors:**

- Never modify existing Flyway migrations
- Always create new migration files with incremented version
- Test migrations on clean database first

**5. MapStruct Compilation Issues:**

- Clean and rebuild: `./gradlew clean build`
- Check annotation processor is configured
- Verify mapper interfaces are in `mappers` package

## Key Dependencies

All services share common dependencies defined in root `build.gradle`:

- **Spring Boot:** 3.4.4 - 3.5.6
- **Spring Cloud:** 2024.0.1 - 2025.0.0
- **MapStruct:** 1.5.5.Final
- **Lombok:** Annotation processor
- **Flyway:** 10.20.0
- **PostgreSQL:** JDBC driver
- **Kafka:** Spring Kafka
- **JUnit:** 5.10.2

## External Dependencies

Required infrastructure for running webstore:

1. **PostgreSQL 17** (localhost:5432/webstore)
2. **Apache Kafka 4** with broker and topics configured
3. **Git Repository** for Config Server: https://github.com/otabek-samatov/webstore-config

## Project Status

**Recent Development Focus (as of Dec 26, 2025):**

- Kafka implementation across services
- REST API completion
- Payment Service implementation
- Order Service implementation
- Cart Service implementation
- Service discovery setup

**Not Yet Implemented:**

- Auth Service (Spring Security with JWT/OAuth2)
- Docker/Kubernetes deployment configurations
- Comprehensive integration tests
- API documentation (Swagger/OpenAPI)
