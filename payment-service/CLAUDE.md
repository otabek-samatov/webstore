# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The Payment Service is part of the Webstore e-commerce microservices application. It handles payment processing and
refund operations for customer orders, and notifies order-service of payment outcomes over Kafka via a
**transactional outbox**.

**Tech Stack:**

- Java 21
- Spring Boot 3.5.6
- Spring Data JPA (Hibernate)
- PostgreSQL 17
- Spring Cloud (Config, Eureka)
- Spring Kafka — **producer only** (idempotent, non-transactional; see [Kafka & Outbox](#kafka--outbox))
- Spring `@Scheduled` — drives the outbox poller / recovery / cleanup (`@EnableScheduling` on the app class)
- MapStruct for DTO mapping
- Flyway for database migrations
- Lombok for boilerplate reduction
- Jackson `ObjectMapper` — serializes outbox payloads to JSON

**Port:** `8078` (from Config Server; `payment_schema`). The source-tree `application.yml` carries only bootstrap
config (app name + `config.import: optional:configserver:`).

## Build Commands

```bash
# Build without tests
./gradlew :payment-service:build -x test

# Build with tests
./gradlew :payment-service:build

# Clean build
./gradlew :payment-service:clean :payment-service:build

# Run application locally (requires PostgreSQL, Config Server, Eureka, and Kafka running)
./gradlew :payment-service:bootRun

# Regenerate MapStruct implementations
./gradlew :payment-service:compileJava
```

## Architecture

### Microservice Context

Payment Service is one of 8 microservices in the Webstore application. It integrates with:

- **Order Service**: calls `POST /v1/payments` (charge on order creation / payment retry); receives
  asynchronous payment-status events back from this service over Kafka.
- **Config Server**: retrieves runtime configuration.
- **Eureka**: registers for service discovery.
- **Kafka**: publishes payment-status events on `${topic.payment.status}`.

### Service Layer Architecture

```
Controller (PaymentController)
    ↓
Manager (PaymentManager)            → OutboxPublisher (writes outbox_events in the same tx)
    ↓                                        ↓ (async, separate thread)
Repositories (Payment/Refund)        OutboxPoller → OutboxEventProcessor → KafkaTemplate
    ↓
Entities (Payment, Refund : CoreEntity)
    ↓
PostgreSQL (payment_schema)
```

1. **Controller Layer** (`controllers/`)
    - `PaymentController`: REST endpoints for payment and refund operations
   - `RestExceptionHandler`: global exception handling with `@RestControllerAdvice`

2. **Manager Layer** (`managers/`)
    - `PaymentManager`: core business logic, transaction boundaries, validation
    - `PaymentProcess`: interface for payment-gateway abstraction (Strategy pattern)
    - `PaymentMockProxy`: mock implementation (simulates external gateway; ~99% success, time-based)

3. **Repository Layer** (`repositories/`)
    - `PaymentRepository`: `findPaymentByOrderId(Long)` + JPQL `getCountByOrderAndStatus(orderId, status)`
    - `RefundRepository`: derived query `findAllByPayment_Id(Long)`

4. **Outbox infrastructure** (`outbox/`) — see [Kafka & Outbox](#kafka--outbox).

5. **Data Layer**
    - **Entities** (`entities/`): `CoreEntity` `@MappedSuperclass` + `Payment`, `Refund`, `PaymentStatus`
    - **DTOs** (`dto/`, `dto/kafka/`): request/response objects and the Kafka payload
    - **Mappers** (`mappers/`): MapStruct interfaces (implementations auto-generated)

### Key Design Patterns

- **Strategy Pattern**: `PaymentProcess` lets the gateway implementation be swapped
- **Transactional Outbox**: payment-status events are written to `outbox_events` in the same DB transaction
  as the payment change, then published asynchronously — no Kafka transaction, no dual-write window
- **Shared base entity**: `CoreEntity` (`@MappedSuperclass`) centralizes `id` (sequence-generated),
  `@Version`, and Hibernate-proxy-safe `equals`/`hashCode`
- **DTO Pattern** with MapStruct (`componentModel = SPRING`)
- **Optimistic Locking**: `@Version` on every entity

### Transaction Boundaries

`PaymentManager.processPayment()` and `processRefund()` are `@Transactional`. The read methods are not.

**Order-status notification**: `updateOrderStatus(Payment)` builds a `PaymentStatusMessage`
(`orderId`, `actionType = paymentStatus.name().toLowerCase()`) and calls
`outboxPublisher.publishPaymentStatusEvent(msg)`. That inserts an `outbox_events` row **inside the
manager's transaction** (propagation `MANDATORY`), so the payment change and the outbound event commit
atomically. The actual Kafka send happens later on the poller thread.

> Historical note: earlier revisions sent the notification via a `KafkaService` / `OrderStatusKafka`
> and, before that, threw `UnsupportedOperationException`. Both are gone — the outbox is now the only path.

**Hibernate DDL**: `spring.jpa.hibernate.ddl-auto = validate`. Hibernate only validates that the schema
matches the entities; **all** schema changes go through Flyway.

<a id="kafka--outbox"></a>### Kafka & Outbox

payment-service is a **Kafka producer only** — there is no `@KafkaListener` in this service.

**`KafkaConfig`:**

- **Idempotent, non-transactional** producer: `enable.idempotence=true`, `acks=all`,
  `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`. There is **no**
  `transactional.id` and **no** `KafkaTransactionManager` — the outbox replaces Kafka-side transactions.
- Key and value serializers are both `StringSerializer`. The outbox stores already-serialized JSON, so the
  value is sent as a plain `String`.
- Single `KafkaTemplate<String, String>` bean — this is what `OutboxEventProcessor` injects.
- A `NewTopic` bean auto-creates `${topic.payment.status}` with `${num.partitions}` / `${replication.factor}`.

**Outbox components (`outbox/`):**

| Class                   | Role                                                                                                                                                                                                                                            |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OutboxEvent`           | JPA entity for `outbox_events` (UUID id via `GenerationType.UUID`, `@Version`, `aggregateType`, `aggregateId`, `eventType`, `topicName`, `payload TEXT`, `status`, timestamps)                                                                  |
| `OutboxStatus`          | Enum: `PENDING`, `PROCESSING`, `SENT`, `FAILED`                                                                                                                                                                                                 |
| `OutboxEventRepository` | `findTop50ByStatusOrderByCreatedAtAsc`, `claimEvent`, `markSent`, `markPendingForRetry`, `recoverStuckEvents`, `deleteSentBefore`                                                                                                               |
| `OutboxPublisher`       | Write-side API. `@Transactional(MANDATORY)` — must run inside the caller's tx. `publishPaymentStatusEvent(PaymentStatusMessage)` serializes to JSON and inserts a row. (A generic `publish(...)` overload also exists but is currently unused.) |
| `OutboxPoller`          | `@Scheduled` — every `outbox.poll-interval-ms` (default 5 s) loads up to 50 oldest PENDING events and hands each to `OutboxEventProcessor`; `recoverStuckEvents` every `outbox.recovery-interval-ms` (default 60 s)                             |
| `OutboxEventProcessor`  | Per event: `claimEvent` (PENDING → PROCESSING), blocking `kafkaTemplate.send(...).get()`, then `markSent`; on exception `markPendingForRetry`. **No** method-level `@Transactional`                                                             |
| `OutboxCleaner`         | `@Scheduled(cron = ${outbox.cleanup-cron})` (default `0 0 3 * * *`). Deletes SENT events older than `outbox.retention-days` (default 3) in batches of 1000 via native delete                                                                    |
| `OutboxProperties`      | `@ConfigurationProperties("outbox")`                                                                                                                                                                                                            |

`@EnableScheduling` and `@EnableConfigurationProperties(OutboxProperties.class)` are on
`PaymentServiceApplication`.

**Published event** (`dto/kafka/PaymentStatusMessage`): `{ orderId: long, actionType: String }`, where
`actionType` is the lowercased `PaymentStatus` name (`completed` / `failed` / `refunded`) — this matches
the field name and casing order-service's consumer expects (it acts on `completed` / `refunded` and
ignores `failed`). The outbox row uses `aggregateType = "Payment"`, `aggregateId = orderId`,
`eventType = actionType`, `topicName = ${topic.payment.status}`, and the Kafka message key is the `orderId`.

**Outbox `outbox.*` configuration** (defaults from `OutboxProperties`; none currently set in
`webstore-config` — override under `outbox:` in `C:\Projects\webstore-config\config\payment-service.yml`):

| Property                         | Default       | Purpose                                    |
|----------------------------------|---------------|--------------------------------------------|
| `outbox.poll-interval-ms`        | `5000`        | Delay between poll runs                    |
| `outbox.recovery-interval-ms`    | `60000`       | Delay between stuck-event recovery runs    |
| `outbox.stuck-threshold-minutes` | `5`           | Age at which PROCESSING rows are reset     |
| `outbox.retention-days`          | `3`           | How long SENT rows are kept before cleanup |
| `outbox.cleanup-cron`            | `0 0 3 * * *` | Cron for `OutboxCleaner.cleanup`           |

### Database Schema

Tables managed by Flyway (`src/main/resources/db/migration/`):

- **payment** — `id` (BIGINT PK, `payment_seq`), `order_id` (unique, NOT NULL), `user_id` (NOT NULL),
  `payment_status` (VARCHAR, enum `COMPLETED`/`FAILED`/`REFUNDED`), `amount` (DECIMAL(9,2), NOT NULL),
  `created_at` (`@CreationTimestamp`), `version` (NOT NULL). Indexes: `idx_payment_order_id`,
  `idx_payment_user_id`; unique constraint `uc_payment_order (order_id)`.
- **refund** — `id` (BIGINT PK, `refund_seq`), `payment_id` (NOT NULL, **unique**, FK → `payment`),
  `created_at` (`@CreationTimestamp`), `version` (NOT NULL). Index: `idx_refund_payment_id`. The refund
  carries **no** amount or status column (removed in V5).
- **outbox_events** — `id UUID PK`, `version`, `aggregate_type`, `aggregate_id`, `event_type`,
  `topic_name`, `payload TEXT`, `status` (`PENDING` default / `PROCESSING` / `SENT` / `FAILED`),
  `created_at`, `processed_at`. Partial indexes `idx_outbox_status_created (status, created_at) WHERE
  status='PENDING'` and `idx_outbox_processing (status, created_at) WHERE status='PROCESSING'`.

**Sequences:** `payment_seq`, `refund_seq` — both `INCREMENT BY 50` to match the Hibernate pooled
optimizer (`allocationSize = 50`). `outbox_events` uses `GenerationType.UUID` (no DB sequence).

**Migrations:**

- `V1__init_tables.sql` — initial `payment` / `refund` tables, sequences, indexes
- `V2__add_unique_constraint.sql` — `uc_payment_order` unique on `payment.order_id`
- `V3__add_index.sql` — `idx_refund_payment_id`
- `V4__add_columns.sql` — `created_at` columns; `payment.amount` NOT NULL
- `V5__refactor_entities.sql` — `version` NOT NULL on both tables; **drops** `refund.refund_amount` /
  `refund.refund_status`; unique constraint on `refund.payment_id` (now `@OneToOne`); sequences → `INCREMENT BY 50`
- `V6__create_outbox_table.sql` — `outbox_events` table + partial indexes

### Payment Processing Flow

1. Client `POST /v1/payments` with `PaymentDto`; `@Valid` runs Bean Validation.
2. `PaymentManager.processPayment()`:
    - Validates non-null `orderId` / `userId` and non-negative `amount`.
    - Guards against re-charging an already-paid order: `getCountByOrderAndStatus(orderId, COMPLETED) > 0`
      → `IllegalArgumentException`.
    - **Re-attempt aware**: `findPaymentByOrderId(orderId)` — if a row exists (a previous **FAILED**
      attempt) it is **updated in place**; otherwise a fresh entity is mapped via `paymentMapper.toEntity`.
      Keeps the unique `order_id` constraint while letting order-service's retry flow re-charge without a
      duplicate-key violation.
    - Calls `PaymentProcess.processPayment()`; sets status `COMPLETED` or `FAILED`.
    - `paymentRepository.save(payment)`.
    - `updateOrderStatus(payment)` → outbox row (see [Kafka & Outbox](#kafka--outbox)).
3. Returns `PaymentDto` with id and status.

### Refund Processing Flow

1. Client `POST /v1/payments/refund` with `RefundDto` (`paymentId` only; `@Valid`).
2. `PaymentManager.processRefund()`:
    - Validates non-null `paymentId`; loads the payment (`getPaymentById`, 404 if absent).
    - Rejects unless `paymentStatus == COMPLETED` (`IllegalArgumentException`).
    - Maps a `Refund` via `refundMapper.toEntity`, then `refund.setPayment(payment)` (the mapper ignores
      `payment`, so this link must be set explicitly — `payment_id` is NOT NULL/unique).
    - Calls `PaymentProcess.processRefund()`; on success sets `payment` → `REFUNDED`, saves the payment,
      and publishes the status event.
    - `refundRepository.save(refund)`.
3. Returns `RefundDto` (with `paymentId` mapped from `refund.payment.id`).

**Business rules:** only `COMPLETED` payments are refundable; payment becomes `REFUNDED` on success.
The `@OneToOne` + unique `payment_id` enforces **one refund per payment**.

## API Endpoints

### Payment

- `POST /v1/payments` — create/process a payment
- `GET /v1/payments/{paymentID}` — get payment by ID
- `GET /v1/payments/order/{orderID}` — get payment by order ID

### Refund

- `POST /v1/payments/refund` — create/process a refund
- `GET /v1/payments/refund/{refundID}` — get refund by ID
- `GET /v1/payments/refund/payment/{paymentID}` — list refunds for a payment (`List<RefundDto>`)

## Entities & Validation

### CoreEntity (`@MappedSuperclass`)

- `id` — `GenerationType.SEQUENCE`, generator name `entity_seq`; each subclass binds it to its own DB
  sequence via
  `@SequenceGenerator(name = "entity_seq", sequenceName = "<table>_seq", allocationSize = 50, initialValue = 1)`
- `version` — `@Version`, NOT NULL
- Hibernate-proxy-safe `equals()` / `hashCode()`

### Payment

- Fields: `orderId` (unique, `@NotNull`), `userId` (`@NotNull`), `paymentStatus` (`@Enumerated(STRING)`),
  `amount` (`@NotNull @PositiveOrZero`, DECIMAL(9,2)), `createdAt` (`@CreationTimestamp`)
- Inherits `id` / `version` from `CoreEntity`

### Refund

- Fields: `payment` (`@OneToOne(optional = false, cascade = PERSIST)`), `createdAt` (`@CreationTimestamp`)
- Inherits `id` / `version` from `CoreEntity`
- No amount or status field (the `RefundStatus` enum was removed in the refactor)

### DTOs

- `PaymentDto`: `orderId`, `userId` (`@NotNull`), `amount` (`@NotNull @PositiveOrZero`), `paymentStatus`, `id`
- `RefundDto`: `paymentId` (`@NotNull`), `id`
- Validation is at the controller via `@Valid`, plus programmatic checks in `PaymentManager`.

## Known Issues & Technical Debt

1. **Failed refunds leave no trace.** Since `Refund` no longer has a status field, a refund where
   `PaymentProcess.processRefund()` returns `false` is still saved as a row, the payment stays
   `COMPLETED`, and nothing records the failure. Harmless while the mock always succeeds; revisit if a
   real gateway is wired in.
2. **`processPayment()` re-attempt concurrency** relies on `findPaymentByOrderId()` + optimistic
   `@Version`; concurrent re-attempts for the same order are resolved by the optimistic lock (loser
   fails) rather than a pessimistic lock.
3. `RestExceptionHandler` maps `NullPointerException` → 400, which can mask genuine server-side bugs as
   client errors.

## Development Workflow

1. **Database changes**: add a Flyway migration `V{N}__{description}.sql` (never edit existing ones)
2. **Entity changes**: update the entity; keep `@Version` (via `CoreEntity`); if you add a sequence,
   keep its DB `INCREMENT` in lock-step with `allocationSize`
3. **DTO changes**: update the DTO + validation; MapStruct regenerates the mapper
4. **Business logic**: add to `PaymentManager` with `@Transactional` for writes; emit outbound events via
   `OutboxPublisher` (never call `KafkaTemplate` directly)
5. **New outbound Kafka event**: add a payload DTO, publish via `OutboxPublisher` inside the tx, add a
   `NewTopic` bean if it uses a new topic
6. **Rebuild**: `./gradlew :payment-service:compileJava` to regenerate MapStruct implementations
