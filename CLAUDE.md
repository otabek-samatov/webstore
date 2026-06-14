# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Webstore is a microservices-based e-commerce backend application for selling books. This is a **multi-module Gradle
monorepo** with 8 independent microservices.

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
./gradlew :inventory-service:test

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

The system consists of 8 microservices defined in `settings.gradle`:

| Service               | Port | Purpose                                                                              | Database                        |
|-----------------------|------|--------------------------------------------------------------------------------------|---------------------------------|
| **discovery-service** | 8070 | Service registry using Netflix Eureka. Spring `application.name`: `discovery-server` | N/A                             |
| **config-service**    | 8071 | Centralized configuration via Spring Cloud Config                                    | N/A                             |
| **gateway-service**   | 8072 | API Gateway using Spring Cloud Gateway MVC                                           | N/A                             |
| **product-service**   | 8073 | Book catalog with authors, publishers, categories                                    | PostgreSQL (`product_schema`)   |
| **inventory-service** | 8074 | Stock level management and reservation tracking                                      | PostgreSQL (`inventory_schema`) |
| **user-service**      | 8075 | User registration, authentication, profiles, roles                                   | PostgreSQL (`user_schema`)      |
| **order-service**     | 8077 | Order placement and tracking                                                         | PostgreSQL (`order_schema`)     |
| **payment-service**   | 8078 | Payment processing and refunds                                                       | PostgreSQL (`payment_schema`)   |

> Ports and schema names above are the **defaults** from `webstore-config/config/<service>.yml`. Multi-instance
> deployments override `server.port` per instance to avoid a local port clash, but there is **no**
> Kafka-transactional-ID uniqueness requirement — instances coordinate at the row level via the
> outbox/inbox tables (see Kafka section).

**Service-Specific Documentation:** Each service has its own `CLAUDE.md` file in its directory with detailed
implementation guidance.

### Startup Order

Services must be started in this order for proper operation:

1. **config-service** (8071) - Required by all other services for configuration
2. **discovery-service** (8070) - Required for service discovery (Eureka)
3. **Infrastructure Services:**
    - PostgreSQL (5432)
   - Kafka Broker (9092)
4. **Business Services** (any order):
    - product-service (8073), inventory-service (8074), user-service (8075)
   - order-service (8077), payment-service (8078)
5. **gateway-service** (8072) - API Gateway (routes to other services)

## System Architecture

### Communication Patterns

**1. Synchronous Communication (REST):**

- Services use `RestClient` (Spring Framework) with Eureka service discovery
- Services call each other using service names (e.g., `http://inventory-service/v1/...`)
- Load balancing via `@LoadBalanced` RestClient.Builder

**Key Inter-Service REST Calls:**

- Order Service → Inventory Service (price lookup + stock reservation during order creation /
  item add):
    - `POST http://inventory-service/v1/inventory/prices`
    - `POST http://inventory-service/v1/inventory/reserve-stock`
- Order Service → Payment Service (charge on order creation / payment retry):
    - `POST http://payment-service/v1/payments`

> Order creation does **not** fetch a cart — order items are supplied directly in the
> `CreateOrderDto` request body by the caller.

**2. Asynchronous Communication (Kafka):**

- Event-driven messaging for decoupled operations
- **One delivery design across all business services: the transactional outbox / inbox pattern.**
  Producers use an *idempotent, non-transactional* Kafka producer and write outbound events to an
  `outbox_events` table in the same DB transaction as the business change; a poller publishes them
  asynchronously. Consumers dedup redeliveries via an `inbox_messages` table keyed by a stable
  `messageId`. **No** service uses a Kafka `transactional.id` (see
  [Kafka Configuration Details](#kafka-configuration-details)).
    - **payment-service** — producer only (outbox); publishes payment-status events. No consumer/inbox.
    - **order-service** — both outbox (stock events) and inbox (payment events).
    - **inventory-service** — consumer/inbox (stock events); a string producer is configured but unused.

**Kafka Topics & Event Flows:**

| Topic                  | Producer        | Consumer          | Event Type                                                      | Purpose                         |
|------------------------|-----------------|-------------------|-----------------------------------------------------------------|---------------------------------|
| `stock-status-event`   | order-service   | inventory-service | `StockStatusKafka` (producer) → `StockStatusMessage` (consumer) | Stock commit / release / revert |
| `payment-status-event` | payment-service | order-service     | `PaymentStatusMessage`                                          | Payment status updates          |

> Property keys in code are `topic.stock.status` / `topic.payment.status`; the **values** on the wire
> are `stock-status-event` / `payment-status-event` (set in `webstore-config/config/application.yml`).
> A `topic.order.status` (`order-status-event`) key still exists in config but is **no longer used** by
> payment-service or order-service — the payment→order channel moved to `topic.payment.status`.

**Stock Management Flow:**

1. Order placed → Order Service reserves stock **synchronously over REST**
   (`POST inventory-service/v1/inventory/reserve-stock`) — there is **no** Kafka "reserve" event.
2. Order cancelled / refunded / abandoned after a failed payment → Order Service publishes a
   **`release`** stock event (via its outbox) → Inventory Service frees the reservation (`releaseStock`).
3. Order completed (payment confirmed) → Order Service publishes a **`commit`** stock event →
   Inventory Service finalizes the sale (`commitStock`, decrements physical stock).

**Payment Flow:**

1. Payment processed → Payment Service publishes a `PaymentStatusMessage` on `payment-status-event`
   (via its transactional outbox).
2. Order Service consumes it and transitions the order status (`COMPLETED` / `REFUNDED`).

### Configuration Management

**Spring Cloud Config Server (port 8071):**

- Git-backed configuration: https://github.com/otabek-samatov/webstore-config
- Local clone (authoritative source for all runtime properties): **`C:\Projects\webstore-config`**
- All services fetch configuration from Config Server on startup
- Database connections, Kafka topics, and service-specific properties externalized

**`webstore-config` repository layout:**

```
webstore-config/
└── config/
    ├── application.yml          # shared defaults applied to every service
    ├── discovery-service.yml    # per-service overrides (one file per service)
    ├── gateway-service.yml
    ├── product-service.yml
    ├── inventory-service.yml
    ├── user-service.yml
    ├── order-service.yml
    └── payment-service.yml
```

Spring Cloud Config matches each service's `spring.application.name` to the corresponding `<name>.yml` file
and merges it on top of `application.yml`. To change runtime config (Kafka topic names, partition count,
DB credentials, ports, gateway routes, etc.), edit a file under `C:\Projects\webstore-config\config\` and
commit; the Config Server serves the latest commit from the configured Git remote.

**What lives in `application.yml` (shared by all services):**

- **Eureka client:** `defaultZone: http://localhost:8070/eureka/`, `preferIpAddress: true`
- **Actuator:** `management.endpoints.web.exposure.include: "*"`
- **Datasource:** `jdbc:postgresql://localhost:5432/webstore?currentSchema=${service.schemaName}`,
  user `user` / password `password`, driver `org.postgresql.Driver`
- **JPA/Hibernate:** `ddl-auto: validate` (Flyway is authoritative for schema), `show-sql: true`,
  PostgreSQL dialect
- **Kafka:** `bootstrap.servers: localhost:9092`, `num.partitions: 3`, `replication.factor: 1`
  (sized for a single-broker local Kafka). These are **custom top-level keys** read via `@Value`
  in each service's `KafkaConfig` — they are **not** the standard `spring.kafka.*` properties.
- **Kafka topics:** `topic.stock.status: stock-status-event`, `topic.payment.status: payment-status-event`.
  A legacy `topic.order.status: order-status-event` key still exists but is no longer used.

> Note: the Kafka topic names in the running config are `stock-status-event` / `order-status-event`,
> not `stock-status-topic` / `order-status-topic`. Older references to the `*-topic` names elsewhere in
> docs are stale.

**What lives in each `<service>.yml`:**

- `server.port` — fixed default for the service (see Service Inventory table above)
- `service.schemaName` — PostgreSQL schema injected into the shared datasource URL
- Service-specific overrides (e.g., `gateway-service.yml` defines `spring.cloud.gateway.routes`;
  `discovery-service.yml` overrides Eureka to act as the server with `registerWithEureka: false` /
  `fetchRegistry: false`)

**Gateway routes (defined in `gateway-service.yml`):**

| External path   | Routed to (Eureka name)  |
|-----------------|--------------------------|
| `/inventory/**` | `lb://inventory-service` |
| `/order/**`     | `lb://order-service`     |
| `/payment/**`   | `lb://payment-service`   |
| `/product/**`   | `lb://product-service`   |
| `/user/**`      | `lb://user-service`      |

Each route strips its prefix via `RewritePath=/<prefix>/(?<path>.*), /$\{path}` before forwarding.

**Application Properties Pattern (per service, in source tree):**

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
- Everything else (DB, Kafka, port, schema, etc.) is resolved from the Config Server at startup —
  do **not** duplicate those values into the service's source-tree `application.yml`.

**Editing config: workflow**

1. Edit the relevant file in `C:\Projects\webstore-config\config\`.
2. Commit and push to the Git remote — Config Server reads from Git, not the local working copy.
3. Restart the affected service(s) or hit `/actuator/refresh` on a service with `@RefreshScope` beans.

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
- `inventory_change` audit trail of every stock operation
- `inbox_messages` — consumer-side Kafka dedup (idempotency by `message_id`)

**User Service:**

- `users`, `user_profile`, `address`, `security_role`

**Order Service:**

- `orders`, `order_item` with status, shipping/tax calculations
- `outbox_events` — transactional outbox for outbound Kafka events
- `inbox_messages` — consumer-side dedup for inbound payment events
- `saga_instance` — orchestration-saga lifecycle/audit state for order creation

**Payment Service:**

- `payment`, `refund` — `refund` is now `@OneToOne` to `payment` (one refund per payment; no amount/status columns)
- `outbox_events` — transactional outbox for outbound payment-status Kafka events

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
- Transactional outbox / inbox for reliable Kafka delivery (no Kafka transactions)
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

### Delivery Semantics (transactional outbox / inbox)

All business services use the **transactional outbox / inbox** pattern — there is **no** Kafka
`transactional.id` and **no** `KafkaTransactionManager` anywhere. Delivery is **at-least-once** on the
wire; exactly-once *effects* come from the outbox (write the event in the same DB tx as the business
change) and the inbox (consumer-side dedup by `messageId`). See each service's `CLAUDE.md` for the
full design.

**Producer Configuration (idempotent, non-transactional):**

- `enable.idempotence=true`
- `acks=all` (all replicas must acknowledge)
- `retries=Integer.MAX_VALUE`
- `max.in.flight.requests.per.connection=5`
- Value serializer is `StringSerializer` — the outbox stores already-serialized JSON, sent as a `String`
- A poller (`OutboxPoller` / `OutboxEventProcessor`) claims `PENDING` rows with an atomic conditional
  `UPDATE` and performs the actual `KafkaTemplate.send`

**Consumer Configuration (order-service, inventory-service):**

- Consumer group: `{application-name}-group`
- `isolation.level=read_committed`, `enable.auto.commit=false` (manual offset management)
- RECORD-level acknowledgment mode; `@Transactional` listener (the inbox row, business change, and any
  outbox rows commit together, then the offset commits)
- Value deserializer is `JsonDeserializer<>(...)` with a constructor-configured target type (the wire
  payload has no `__TypeId__` header since producers send with `StringSerializer`)

> payment-service is **producer-only** (no `@KafkaListener`); inventory-service configures an idempotent
> string producer for symmetry but does not currently publish.

### Multi-Instance Deployment

No service uses a Kafka `transactional.id`, so the old "unique port per instance for the transactional
ID" constraint **does not apply** to any service. Instances coordinate at the **row level** instead — the
outbox poller claims rows with an atomic conditional `UPDATE`, and the inbox dedups on the `messageId`
primary key — so all services are safe to run concurrently regardless of port. (Per-instance `server.port`
overrides are still needed only to avoid a local port clash.)

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
- If outbound events aren't published, check the `outbox_events` table — rows stuck in `PROCESSING`
  point at a send failure (the poller resets stuck rows to `PENDING`); confirm the producer is
  **non-transactional** (a stray `transactional.id` makes `send()` outside a tx throw)
- Verify topic creation with correct partitions/replication
- Check consumer group configuration and that producer/consumer field names + `actionType` casing match

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

> For current development activity, consult git history (branches, recent commits, open PRs) rather
> than a hand-maintained list here — it stays accurate without manual upkeep.

**Not Yet Implemented:**

- Auth Service (Spring Security with JWT/OAuth2)
- Docker/Kubernetes deployment configurations
- Comprehensive integration tests
- API documentation (Swagger/OpenAPI)
