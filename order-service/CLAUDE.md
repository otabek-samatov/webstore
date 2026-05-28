# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Gradle-based Spring Boot project using Java 21.

```bash
# Build the project
./gradlew :order-service:build

# Run tests
./gradlew :order-service:test

# Clean build
./gradlew :order-service:clean :order-service:build

# Run the application
./gradlew :order-service:bootRun

# Skip tests during build
./gradlew :order-service:build -x test
```

## Architecture Overview

### Service Role

The order-service is a microservice responsible for managing customer orders in the webstore distributed e-commerce
system. Responsibilities include:

- Order creation, retrieval, and status management
- Order item management (add / remove items on existing orders)
- Synchronous price lookup and stock reservation against `inventory-service` (REST)
- Publishing stock lifecycle events to inventory-service (Kafka, via a **transactional outbox**)
- Reacting to payment lifecycle events from payment-service (Kafka, deduped via a
  **transactional inbox**)
- Driving the multi-step order-creation flow as an **orchestration-based saga** with
  compensating actions on failure (see the [Orchestration Saga](#orchestration-saga)
  section)

### Layered Architecture

```
Controllers (REST API)
    ↓
Managers (Business Logic)
    ↓
Repositories (Spring Data JPA)
    ↓
PostgreSQL Database
```

Manager layer:

- `OrderManager` (`@Service`) — core order/order-item business logic and persistence.
  `createOrder` delegates to `CreateOrderSaga`; `retryPayment` re-charges a
  `PAYMENT_FAILED` order via `PaymentClient`; the other write paths
  (`changeOrderStatus`, `addItemsToOrder`, `removeOrderItem`) write outbound events to
  the outbox **in the same DB transaction** as the order mutation, via `OutboxPublisher`.
- `PaymentClient` (`@Service`, package `orderservice.client`) — thin REST client over
  payment-service's `POST /v1/payments`, shared by `ProcessPaymentStep` and `retryPayment`.
- `KafkaConsumerService` (`@Service`) — inbound order-status (payment) events. Every
  handled event is funneled through `InboxProcessor.processOnce` for structural
  exactly-once processing.

Outbox infrastructure (package `orderservice.outbox`) handles outbound publishing
asynchronously — see the [Outbox Pattern](#outbox-pattern) section. Inbox
infrastructure (package `orderservice.inbox`) handles consumer-side dedup — see
the [Inbox Pattern](#inbox-pattern) section. Saga infrastructure (package
`orderservice.saga`) orchestrates multi-step order workflows with compensating
actions — see the [Orchestration Saga](#orchestration-saga) section.

### Key Architectural Patterns

**1. DTO Pattern with MapStruct**

- All entities have corresponding DTOs for API contracts
- MapStruct generates type-safe mappers at compile time
- Mappers live in `orderservice.mappers` package
- Mappers use `componentModel = "spring"` and `unmappedTargetPolicy = ReportingPolicy.IGNORE`

**2. Embedded Value Objects**

- `Address` is an `@Embeddable` component within `Order`
- Flattened in database as columns on the `orders` table (`country`, `region`, `city`, `street`, `address_line`)

**3. Optimistic + Pessimistic Locking**

- Both `Order` and `OrderItem` (via `CoreEntity`) carry a `@Version` field for optimistic locking
- `OrderRepository.findByIdForUpdate` uses `LockModeType.PESSIMISTIC_WRITE` for write-path operations
  (`changeOrderStatus`, `addItemsToOrder`, `removeOrderItem`) to serialize concurrent mutations on the same order

**4. Encapsulated Order Aggregate**

- `Order` exposes `getItems()` as an immutable copy (`Set.copyOf`); item collection is mutated only via
  `Order.addItem` / `Order.removeItem`
- Both `addItem` and `removeItem` reject mutations unless `orderStatus == NEW`
- `Order.setOrderStatus` enforces a valid state-machine transition via `OrderStatus.isAcceptableNextStatus`;
  same-status assignment is a silent no-op

**5. Transactional Processing**

- `OrderManager` write methods use `@Transactional`; read methods use `@Transactional(readOnly = true)`
- `KafkaConsumerService` listener is `@Transactional` (DB transaction)
- `OutboxPublisher.publish` / `.publishOrderItemEvent` use `@Transactional(propagation = MANDATORY)` —
  they must run inside the caller's DB transaction so the outbox row and the business change commit
  atomically. There is no Kafka-side transaction; the Kafka send is performed later by the poller
  with an **idempotent** (not transactional) producer.
- `InboxProcessor.processOnce` / `.recordIfNew` use `@Transactional(propagation = MANDATORY)` —
  the inbox row, the handler's side effects, and any outbox rows it writes must all commit in
  the listener's single DB transaction.

**6. Transactional Outbox (outbound Kafka)**

- Replaces the previous Kafka-transactional producer pattern. See the
  [Outbox Pattern](#outbox-pattern) section for the full design.

**7. Transactional Inbox (inbound Kafka)**

- Consumer-side dedupe by stable `messageId`. Pairs with the outbox to give the
  order ↔ payment ↔ inventory choreography saga end-to-end exactly-once semantics
  on top of at-least-once Kafka delivery. See the [Inbox Pattern](#inbox-pattern)
  section for the full design.

**8. Orchestration-Based Saga (multi-step write workflows)**

- A small in-process orchestrator (`SagaOrchestrator`) sequences ordered
  `SagaStep`s, persists lifecycle state in `saga_instance`, and runs registered
  compensations in reverse order if any step throws. The only concrete saga today
  is `CreateOrderSaga`, which replaces the previous synchronous body of
  `OrderManager.createOrder`. See the [Orchestration Saga](#orchestration-saga)
  section for the full design.

### Inter-Service Communication

**Synchronous (REST) — outbound to inventory-service:**

Uses a Eureka-aware `@LoadBalanced` `RestClient` from `RestConfig`. Two integration points exist:

| Call                                                       | Triggered by                                                                                | Failure → exception                                             |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `POST http://inventory-service/v1/inventory/prices`        | `PriceItemsStep` of `CreateOrderSaga` (on create), `OrderManager.addItems` (on item add)    | 4xx → `IllegalArgumentException`, 5xx → `IllegalStateException` |
| `POST http://inventory-service/v1/inventory/reserve-stock` | `ReserveStockStep` of `CreateOrderSaga` (on create), `OrderManager.addItems` (on item add)  | 4xx → `NotEnoughStockException`, 5xx → `IllegalStateException`  |
| `POST http://payment-service/v1/payments`                  | `PaymentClient`, called by `ProcessPaymentStep` (on create) and `OrderManager.retryPayment` | 4xx → `PaymentFailedException`, 5xx → `IllegalStateException`   |

The inventory calls send/receive `List<InventoryDto>` payloads. `InventoryDto` carries `productSKU`,
`stockLevel`, `reservedStock`, `stockPrice`, `sellPrice`, and `measurementUnit`. The payment call is
issued via `PaymentClient` (package `orderservice.client`) and sends/receives a single `PaymentDto`
(`orderId`, `userId`, `amount`, `paymentStatus`). A 4xx/5xx maps to the exceptions above; a **200 with
`paymentStatus = FAILED`** is a *declined* payment — handled by the caller (order → `PAYMENT_FAILED`),
not an exception.

> Note: `createOrder` does **not** call cart-service. Items are supplied directly in the `CreateOrderDto`
> request body by the caller.

**Asynchronous (Kafka):**

- **Produces:** stock-status events on `${topic.stock.status}` (key: `orderId` as a string).
  Payload is a JSON-serialized `List<OrderItemDto>` (whatever was passed to
  `OutboxPublisher.publishOrderItemEvent`). Publishing is **outbox-driven** — see the
  [Outbox Pattern](#outbox-pattern) section.
- **Consumes:** `OrderStatusKafka` events from `${topic.order.status}` (payment-service is the producer).
  Every event is funneled through `InboxProcessor.processOnce` (see the
  [Inbox Pattern](#inbox-pattern) section), so duplicate redeliveries become a structural no-op.
- **Delivery semantics:** at-least-once on the producer side. The consumer side uses
  `read_committed` + manual offset commit inside a DB transaction, *and* the inbox guarantees
  the handler runs at most once per `messageId`.

### Kafka Integration Details

**Producer Configuration (`KafkaConfig`):**

- **Idempotent**, **non-transactional** producer (`enable.idempotence=true`, `acks=all`,
  `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`). There is no
  `transactional.id` and no `KafkaTransactionManager` bean — the transactional outbox replaces
  Kafka-side transactions.
- Single `KafkaTemplate<String, String>` (`stringKafkaTemplate`) bean — both the key and the value
  are strings (the value is the pre-serialized JSON payload column from the outbox row).
- Topic `${topic.stock.status}` is auto-created via a `NewTopic` bean with `${num.partitions}` /
  `${replication.factor}` from Config Server.

**Producer Usage (via outbox):**

`OrderManager` does **not** call the `KafkaTemplate` directly. Instead it calls
`OutboxPublisher.publishOrderItemEvent(orderId, actionType, items)` inside its existing
`@Transactional` boundary, which inserts a row into `outbox_events`. `OutboxPoller` later picks
up the row and `OutboxEventProcessor` performs the actual `kafkaTemplate.send(topic, key, payload)`.

Action types in use (the `eventType` column of the outbox row, also the `actionType` semantically
consumed by inventory-service):

- `"release"` — emitted on `CANCELLED` / `REFUNDED` status, and on item removal via `removeOrderItem`
- `"commit"` — emitted on `COMPLETED` status

**Consumer Configuration:**

- Consumer group: `{spring.application.name}-group`
- `isolation.level=read_committed`, `enable.auto.commit=false`
- RECORD-level acknowledgment mode (offsets committed inside the `@Transactional` listener)
- Concurrency set to `${num.partitions}`

**Consumer Behavior (`KafkaConsumerService.handleOrderStatusUpdate`):**

- Listener signature is `(ConsumerRecord<String, OrderStatusKafka> record, @Header("X-Message-Id",
  required=false) String messageIdHeader)` — Kafka metadata is exposed so it can be stored on
  the inbox row.
- Ignores events with `null` orderId (logs warn, returns)
- Maps `actionType`: `"Completed"` → `OrderStatus.COMPLETED`, `"Refunded"` → `OrderStatus.REFUNDED`.
  Any other `actionType` is logged and ignored.
- Computes an idempotency key: prefers the `X-Message-Id` header (via `StringUtils.hasText`);
  otherwise falls back to the stable business key `order-status:{orderId}:{actionType}`.
  **Never** uses `topic-partition-offset` — producer retries can land the same logical event at
  a different offset, which would defeat dedup.
- Builds an `InboxMessage` via `InboxProcessor.fromKafkaRecord(...)` and wraps the business call
  in `inboxProcessor.processOnce(msg, () -> orderManager.changeOrderStatus(...))`. If
  `processOnce` returns `false`, the event is a duplicate — the handler is skipped and the
  listener returns normally so the Kafka offset still commits.
- The inbox-row insert, the order mutation, and any outbox rows written downstream all commit
  in the listener's single `@Transactional` boundary.

> **Note on early returns:** the null-orderId and unknown-actionType branches return *before*
> `processOnce`, so nothing is recorded for those cases. A redelivery of an unknown event will
> re-enter the handler and re-log; harmless today, but worth knowing if log volume becomes an
> issue.

**Multi-Instance Deployment:**

- Multiple instances are safe to run concurrently. The previous "unique port for unique
  transactional ID" requirement no longer applies — there is no Kafka transactional ID.
- The outbox handles cross-instance coordination at the **row level** via
  `OutboxEventRepository.claimEvent` (atomic `UPDATE ... WHERE id = ? AND status = 'PENDING'`).
  Only one instance wins the claim; the others see `claimed = false` and skip the row.

### Outbox Pattern

The order-service publishes outbound Kafka events via a **transactional outbox** (package
`orderservice.outbox`). The pattern guarantees that an event is recorded **iff** the business
DB change commits, eliminating the dual-write inconsistency window between "DB committed"
and "Kafka send acknowledged".

**Components:**

| Class                   | Role                                                                                                                                                                                                                                                                           |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OutboxEvent`           | JPA entity for the `outbox_events` row (UUID id, `@Version`, status, payload, etc.)                                                                                                                                                                                            |
| `OutboxStatus`          | Enum: `PENDING`, `PROCESSING`, `SENT`, `FAILED`                                                                                                                                                                                                                                |
| `OutboxEventRepository` | JPA repo with `claimEvent`, `markSent`, `markPendingForRetry`, `recoverStuckEvents`, `deleteSentBefore`                                                                                                                                                                        |
| `OutboxPublisher`       | Write-side API. `@Transactional(MANDATORY)` — must be called inside an existing DB tx. Serializes payload to JSON and inserts an `OutboxEvent` row.                                                                                                                            |
| `OutboxPoller`          | `@Scheduled` job: every `outbox.poll-interval-ms` (default 5 s) loads up to 50 oldest PENDING events and hands each to `OutboxEventProcessor`. Also runs `recoverStuckEvents` every `outbox.recovery-interval-ms` (default 60 s).                                              |
| `OutboxEventProcessor`  | Per-event flow: claim (PENDING → PROCESSING), Kafka send (blocking `future.get()`), then `markSent`. On exception → `markPendingForRetry`. **No method-level `@Transactional`** — each repo method runs in its own short tx so the row lock is released before the Kafka send. |
| `OutboxCleaner`         | `@Scheduled(cron = ${outbox.cleanup-cron})` (default `0 0 3 * * *` = 3 AM daily). Deletes SENT events older than `outbox.retention-days` (default 3) in batches of 1000 via a native `DELETE ... WHERE id IN (SELECT ... LIMIT ...)` query.                                    |
| `OutboxProperties`      | `@ConfigurationProperties("outbox")` — see [Outbox configuration](#outbox-configuration) below.                                                                                                                                                                                |

`@EnableScheduling` and `@EnableConfigurationProperties(OutboxProperties.class)` are declared
on `OrderServiceApplication`.

**End-to-end flow (status-change example):**

1. `OrderManager.changeOrderStatus` runs in a `@Transactional` boundary.
2. It mutates `Order`, then calls `outboxPublisher.publishOrderItemEvent(orderId, actionType, items)`.
3. `OutboxPublisher` (propagation `MANDATORY`) inserts an `outbox_events` row in the **same** tx.
4. The tx commits — the order change and the outbox row are durably linked.
5. Within seconds, `OutboxPoller` picks up the PENDING row.
6. `OutboxEventProcessor.processEvent` claims the row (atomic conditional UPDATE), sends via
   `KafkaTemplate`, then marks it SENT.
7. If the send fails, `markPendingForRetry` flips it back to PENDING for the next poll cycle.
8. If the processor crashes mid-send (row stuck in PROCESSING), `recoverStuckEvents` resets
   rows whose `createdAt` is older than `outbox.stuck-threshold-minutes` (default 5).

**Outbox row schema (column → field):**

- `id` UUID — `OutboxEvent.id` (`GenerationType.UUID`)
- `version` INT — `@Version`
- `aggregate_type` — currently `"order-service"` for stock-status events (note: this is the
  service name; the generic `publish(...)` API expects an aggregate name like `"Order"`)
- `aggregate_id` — the order id as a string; also used as the Kafka message key
- `event_type` — `"release"` or `"commit"` for stock events
- `topic_name` — captured at publish time (currently `${topic.stock.status}`)
- `payload` TEXT — Jackson-serialized JSON of the input object
- `status` — `PENDING` / `PROCESSING` / `SENT` / `FAILED`
- `created_at` (`@CreationTimestamp`), `processed_at` (set when marked SENT)

**Indexes** (partial, filtered on status — see `V2__create_outbox_table.sql`):

- `idx_outbox_status_created (status, created_at) WHERE status = 'PENDING'` — poller query
- `idx_outbox_processing (status, created_at) WHERE status = 'PROCESSING'` — recovery query

**Delivery semantics & idempotency:**

- **At-least-once**, not exactly-once. The Kafka producer is `enable.idempotence=true` (dedupes
  retries within a single producer session) but the outbox pattern itself can cause duplicates
  in two cases:
    1. Processor sends successfully but crashes before `markSent` commits → next poll re-claims
       and re-sends.
    2. A slow send takes longer than `stuck-threshold-minutes` → recovery resets the row to
       PENDING and another instance re-sends while the first send is still in flight.
- **Downstream consumers (inventory-service) MUST be idempotent.** Use `aggregate_id`
  (= orderId) plus `event_type` as the idempotency key, or fold idempotency into the
  business operation (e.g., set-based stock reservation).

**Multi-instance coordination:**

- `claimEvent` is the linearization point: `UPDATE outbox_events SET status='PROCESSING'
  WHERE id = ? AND status = 'PENDING'`. Exactly one instance gets `rowcount = 1`; others
  get 0 and skip.

<a id="outbox-configuration"></a>**Configuration (`outbox.*`):**

| Property                         | Default       | Purpose                                                      |
|----------------------------------|---------------|--------------------------------------------------------------|
| `outbox.poll-interval-ms`        | `5000`        | `@Scheduled(fixedDelay)` between poll runs                   |
| `outbox.recovery-interval-ms`    | `60000`       | `@Scheduled(fixedDelay)` between stuck-event recovery runs   |
| `outbox.stuck-threshold-minutes` | `5`           | Age (from `created_at`) at which PROCESSING events are reset |
| `outbox.retention-days`          | `3`           | How long SENT events are kept before cleanup deletes them    |
| `outbox.cleanup-cron`            | `0 0 3 * * *` | Cron for `OutboxCleaner.cleanup`                             |

None of these properties are currently set in `webstore-config` — defaults from
`OutboxProperties` are used. To override, add them under `outbox:` in
`C:\Projects\webstore-config\config\order-service.yml` and commit/push.

### Inbox Pattern

The order-service deduplicates inbound Kafka events via a **transactional inbox** (package
`orderservice.inbox`). It is the consumer-side counterpart of the outbox: the listener inserts
a row keyed by a stable `messageId` in the same DB transaction as its business side-effects,
so redelivered Kafka messages (producer retry, consumer crash before offset commit, outbox
re-send on the producer's side) become a structural no-op.

Together, the outbox + inbox + `read_committed` consumer give the order ↔ payment ↔ inventory
**choreography saga** end-to-end exactly-once semantics on top of at-least-once Kafka delivery.

**Components:**

| Class                    | Role                                                                                                                                                                                                                                                                                                                                     |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InboxMessage`           | JPA entity for the `inbox_messages` row. `messageId` (String) is the PK — the unique constraint *is* the dedup mechanism. Stores Kafka coordinates (topic / partition / offset) and the JSON payload alongside `aggregateType` / `aggregateId` / `eventType` / `status`.                                                                 |
| `InboxStatus`            | Enum: `RECEIVED`, `PROCESSED`, `FAILED`                                                                                                                                                                                                                                                                                                  |
| `InboxMessageRepository` | JPA repo. Key methods: `existsByMessageId` (fast-path dedup check), `markProcessed`, `markFailed`, `deleteProcessedBefore` (native batched cleanup).                                                                                                                                                                                     |
| `InboxProcessor`         | Public API. `@Transactional(MANDATORY)` — must be called inside the listener's DB tx. Two entry points: `processOnce(message, Runnable)` (record → run handler → mark PROCESSED) and the lower-level `recordIfNew(message)` returning a boolean. Also exposes `fromKafkaRecord(...)` to build an `InboxMessage` from a `ConsumerRecord`. |
| `InboxCleaner`           | `@Scheduled(cron = ${inbox.cleanup-cron})` (default `0 30 3 * * *` — 3:30 AM daily, staggered after the outbox cleanup at 3:00 AM). Deletes PROCESSED messages older than `inbox.retention-days` (default 7) in batches of 1000 via a native `DELETE ... WHERE message_id IN (SELECT ... LIMIT ...)` query.                              |
| `InboxProperties`        | `@ConfigurationProperties("inbox")` — see [Inbox configuration](#inbox-configuration) below.                                                                                                                                                                                                                                             |

`@EnableConfigurationProperties` on `OrderServiceApplication` registers both
`OutboxProperties.class` and `InboxProperties.class`.

**End-to-end flow (inbound order-status example):**

1. `KafkaConsumerService.handleOrderStatusUpdate` is invoked with a `ConsumerRecord` and an
   optional `X-Message-Id` header. The method is `@Transactional`.
2. Null/unknown-actionType guards short-circuit before any DB work.
3. The handler computes an idempotency key (header first, business-key fallback) and builds
   an `InboxMessage` via `InboxProcessor.fromKafkaRecord(...)`.
4. `inboxProcessor.processOnce(msg, () -> orderManager.changeOrderStatus(...))`:
    - calls `recordIfNew(msg)` — `existsByMessageId` short-circuits known duplicates;
      otherwise `repository.save(msg)` inserts a row with `status = RECEIVED`. A
      concurrent insert race surfaces as `DataIntegrityViolationException`, which is
      caught and treated as "duplicate".
    - if new, runs the handler (which mutates the `Order` and writes an outbox row);
    - then `markProcessed(messageId, now)` flips the row to PROCESSED.
5. The listener's `@Transactional` boundary commits **everything** atomically: inbox row,
   order mutation, outbox row.
6. The Kafka offset is committed after the DB tx (RECORD ack mode + `enable.auto.commit=false`).
7. On a redelivery, `recordIfNew` returns `false`, the handler is skipped, the offset still
   commits — the duplicate is silently absorbed.

**Inbox row schema (column → field):**

- `message_id` VARCHAR(255) PK — `InboxMessage.messageId` (the idempotency key)
- `version` INT — `@Version`
- `aggregate_type` — `"Order"` for order-status events
- `aggregate_id` — the order id as a string (nullable for events without one)
- `event_type` — the inbound `actionType` (e.g. `"Completed"`, `"Refunded"`)
- `topic_name`, `partition_no`, `kafka_offset` — Kafka coordinates at receive time
  (informational; **not** used for dedup)
- `payload` TEXT — Jackson-serialized JSON of the event
- `status` — `RECEIVED` (default) / `PROCESSED` / `FAILED`
- `received_at` (`@CreationTimestamp`), `processed_at` (set when marked PROCESSED)

**Indexes** (see `V3__create_inbox_table.sql`):

- `idx_inbox_status_processed_at (status, processed_at) WHERE status = 'PROCESSED'` —
  supports the cleanup query
- `idx_inbox_topic_aggregate (topic_name, aggregate_id)` — supports debugging /
  reconciliation lookups

**Idempotency key strategy (`KafkaConsumerService.idempotencyKey`):**

1. **Prefer** the producer-supplied `X-Message-Id` Kafka header (checked via
   `StringUtils.hasText`). Producers (e.g. payment-service) should stamp this with a
   stable value such as their outbox row's UUID.
2. **Fall back** to a stable business key: `order-status:{orderId}:{actionType}`.
3. **Never** use `topic-partition-offset` — a producer retry can land the same logical
   event at a different offset, which would defeat dedup.

> ⚠️ The business-key fallback is one-shot per `(orderId, actionType)` pair. That's correct
> today because the `OrderStatus` state machine accepts each transition only once. If
> payment-service later emits events that can legitimately repeat for the same pair (e.g.
> partial refunds), the fallback would collide and drop real events — at that point the
> `X-Message-Id` header must be made mandatory.

**Delivery semantics & guarantees:**

- The inbox makes the **business effect** structurally exactly-once. Even if Kafka redelivers
  the same record, `existsByMessageId` short-circuits the handler.
- The PK uniqueness on `message_id` is the linearization point across multiple consumer
  instances. Only one transaction can insert a given `message_id`; concurrent inserts
  surface as `DataIntegrityViolationException` and are caught as "race lost → duplicate".
- The `RECEIVED` → `PROCESSED` transition is purely informational (the row exists either way);
  it's useful for observability and for the cleaner to know which rows are safe to delete.

**Multi-instance coordination:**

- The unique PK on `message_id` does all the work. No `claimEvent`-style update is needed
  because there is no async polling — the handler runs inline in the Kafka listener thread.
- Combined with consumer-group partition assignment, the typical case is a single instance
  handling each message; the inbox still protects against the rebalance edge case where two
  instances briefly believe they own the same partition.

<a id="inbox-configuration"></a>**Configuration (`inbox.*`):**

| Property               | Default        | Purpose                                                                                                                                                     |
|------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `inbox.retention-days` | `7`            | How long PROCESSED messages are kept before cleanup deletes them. Should outlast the producer's outbox retention so late redeliveries are still recognized. |
| `inbox.cleanup-cron`   | `0 30 3 * * *` | Cron for `InboxCleaner.cleanup` (3:30 AM, staggered after outbox cleanup at 3:00 AM)                                                                        |

Neither property is currently set in `webstore-config` — defaults from `InboxProperties` are
used. To override, add them under `inbox:` in
`C:\Projects\webstore-config\config\order-service.yml` and commit/push.

<a id="orchestration-saga"></a>### Orchestration Saga

The order-service runs multi-step write workflows as **orchestration-based sagas**
(package `orderservice.saga`). A small in-process orchestrator drives an ordered list
of `SagaStep`s; if any step throws, the orchestrator invokes the registered
compensation on every previously-succeeded step in reverse order. The pattern is
intentionally minimal — no external framework, no async step queue, no resumption
after process restart.

Today there is exactly one concrete saga: `CreateOrderSaga`, which replaces what used
to be the synchronous body of `OrderManager.createOrder`. The orchestrator and the
saga are designed so that adding a second saga is purely additive — implement
`SagaStep`s, group them in a new `@Component`, and call `orchestrator.execute(...)`.

**Generic framework (package `orderservice.saga`):**

- **`SagaStep`** — Interface. `name()`, `execute(SagaContext)`, and an optional
  `compensate(SagaContext)` (default no-op for read-only or final steps).
- **`SagaContext`** — Mutable per-execution string-keyed map. Also carries the `sagaId`
  (set by the orchestrator after `begin`) so steps can use it as a stable identifier
  (e.g. an outbox aggregateId before the order has an id).
- **`SagaStatus`** — Enum: `STARTED`, `COMPLETED`, `COMPENSATING`, `COMPENSATED`, `FAILED`.
- **`SagaInstance`** — JPA entity persisted to `saga_instance`. UUID id, `@Version`,
  `saga_type`, `current_step`, `status`, `error_message`, `@CreationTimestamp` /
  `@UpdateTimestamp`.
- **`SagaInstanceRepository`** — Plain `JpaRepository<SagaInstance, UUID>`.
- **`SagaStateService`** — All lifecycle writes (`begin`, `updateCurrentStep`,
  `markCompleted`, `markCompensating`, `markCompensated`, `markFailed`) run with
  `@Transactional(propagation = REQUIRES_NEW)` so that the audit trail in `saga_instance`
  survives even if a step's own transaction rolls back. Error messages are truncated to
  4000 chars before persisting.
- **`SagaOrchestrator`** — Drives the flow. For each step: `updateCurrentStep` →
  `step.execute(context)` → push onto the executed list. On the first exception,
  `markCompensating(reason)`, walk the executed list in reverse calling
  `compensate(context)`, then `markCompensated` (or `markFailed` if any compensation
  itself threw). Rethrows as `SagaExecutionException`.
- **`SagaExecutionException`** — RuntimeException carrying `sagaId` and the name of the
  failed step. Mapped by `RestExceptionHandler` only via its underlying cause — the
  orchestrator wraps the original exception so existing handlers
  (`NotEnoughStockException`, `IllegalArgumentException`, etc.) still apply through
  `getCause()`. **TODO:** add an explicit handler if needed.

> The orchestrator deliberately does **not** wrap step execution in a transaction.
> Each step manages its own transactional boundary (e.g. `PersistOrderStep` uses an
> injected `TransactionTemplate` to commit the order). This lets one step commit
> while a later step still has the chance to fail and trigger compensations.

**End-to-end flow (create-order example):**

1. `OrderController.createOrder` calls `OrderManager.createOrder(dto)`.
2. `OrderManager.createOrder` delegates straight to `CreateOrderSaga.execute(dto)`.
3. `CreateOrderSaga` runs DTO validation (`BaseValidator`, `OrderItemValidator`),
   builds a `SagaContext` with the `CreateOrderDto`, and calls
   `orchestrator.execute("create-order", steps, context)`.
4. `SagaOrchestrator` calls `SagaStateService.begin("create-order")` (own
   transaction) which inserts a `saga_instance` row with status `STARTED`, and
   stamps the `sagaId` onto the `SagaContext`.
5. Step-by-step (each preceded by `updateCurrentStep`):
    1. `PriceItemsStep` — POSTs SKUs to `inventory-service/v1/inventory/prices`
       and stores the SKU → unit price map under `CTX_PRICES`. Read-only, no
       compensation.
    2. `ReserveStockStep` — POSTs `List<InventoryDto>` to
       `inventory-service/v1/inventory/reserve-stock`. On success stashes the
       reserved items under `CTX_RESERVED_ITEMS`. On 4xx → `NotEnoughStockException`;
       on 5xx → `IllegalStateException`.
    3. `PersistOrderStep` — within a `TransactionTemplate.execute`, builds the
       `Order` (status `NEW`, hardcoded shipping `100`, address, customer,
       items with prices from `CTX_PRICES`) and `orderRepository.save(...)`.
       Stores the saved entity under `CTX_ORDER`.
    4. `ProcessPaymentStep` — charges the customer via `PaymentClient`
       (`POST payment-service/v1/payments` with a `PaymentDto`: orderId,
       userId = customerId, amount = items + shipping + tax). Stores the returned
       payment id under `CTX_PAYMENT_ID`. Final step — no compensation. There are
       **three** outcomes:
        - **Completed** (`paymentStatus = COMPLETED`): order left `NEW`; the async
          payment event drives `NEW → COMPLETED` (see note below).
        - **Declined** (200 + `paymentStatus = FAILED`): the step sets the order to
          `PAYMENT_FAILED` in its own transaction, **keeps the reserved stock**, and
          returns normally — *not* a saga failure, so **no compensation runs**. The
          customer can retry (see `retryPayment`).
        - **Transport error** (payment-service 4xx → `PaymentFailedException`, 5xx
          → `IllegalStateException`): this *is* a saga failure → compensations run.
6. If every step succeeded (including a *declined* payment, which is a normal
   outcome), `markCompleted(sagaId)` flips the row to `COMPLETED` and `execute`
   returns the `SagaInstance`. `CreateOrderSaga` then reads the saved `Order` out of
   `CTX_ORDER` (status `NEW` on success, `PAYMENT_FAILED` on decline) and returns it.
7. If a step throws, the orchestrator captures the failure, marks the row
   `COMPENSATING`, walks executed steps in reverse calling `compensate(context)`,
   marks `COMPENSATED` (or `FAILED` if a compensation also threw), and rethrows
   a `SagaExecutionException` whose cause is the original exception.

> **Payment vs. order completion.** The saga only *initiates* payment; it
> deliberately leaves the order in `NEW` on success. payment-service publishes an
> `OrderStatusKafka` event (`actionType = COMPLETED`), and order-service's
> `KafkaConsumerService` performs the `NEW → COMPLETED` (or `PAYMENT_FAILED →
> COMPLETED` after a retry) transition asynchronously (which in turn emits a
> `commit` stock event). A declined payment also publishes a `FAILED` event, but
> the consumer ignores unknown `actionType`s — the order's `PAYMENT_FAILED` status
> is set **synchronously** by `ProcessPaymentStep`, so there is no double-handling.

**Compensation details (run in reverse order on failure):**

- `ProcessPaymentStep` has no `compensate` override — it's the final step, and a
  *declined* payment is not a failure (it's an alternate success outcome). Only a
  payment-service **transport error** (4xx/5xx) triggers the compensations below.
- `PersistOrderStep.compensate` opens its own `TransactionTemplate`, reloads the
  order by id, and sets it to `CANCELLED` (`NEW → CANCELLED`). It does **not**
  emit a stock-release event — that is owned by `ReserveStockStep.compensate`, so
  the stock is released exactly once.
- `ReserveStockStep.compensate` opens its own DB transaction via
  `TransactionTemplate` and calls `outboxPublisher.publish("CreateOrderSaga",
  <sagaId>, "release", stockStatusTopic, reservedItems)`. The **saga id** is used
  as the outbox `aggregate_id` (the constant `aggregate_type` is `"CreateOrderSaga"`).
  From `OutboxPoller` onward the lifecycle is the standard outbox flow (Kafka send,
  mark SENT, etc.).
- `PriceItemsStep.compensate` is a no-op (read-only step).

> Failure example (payment-service **down**): `ProcessPaymentStep` throws
> `IllegalStateException` → orchestrator compensates `PersistOrderStep` (order →
> `CANCELLED`) then `ReserveStockStep` (release stock via outbox). Net effect: order
> cancelled, reserved stock freed. Contrast with a **declined** payment, where the
> order is kept as `PAYMENT_FAILED` with stock held for a retry.

**`saga_instance` row schema (column → field):**

- `id` UUID PK — `SagaInstance.id` (`GenerationType.UUID`)
- `version` INT — `@Version`
- `saga_type` — `"create-order"` for the create flow; intended to be a stable
  identifier per saga definition
- `current_step` — name of the last step that started (`"price-items"`,
  `"reserve-stock"`, `"persist-order"`, `"process-payment"`); `null` immediately
  after `begin`
- `status` — `STARTED` / `COMPLETED` / `COMPENSATING` / `COMPENSATED` / `FAILED`
- `error_message` TEXT — populated when status leaves the happy path; truncated
  to 4000 chars
- `created_at` (`@CreationTimestamp`), `updated_at` (`@UpdateTimestamp`)

**Indexes** (see `V4__create_saga_instance_table.sql`):

- `idx_saga_type_status_created (saga_type, status, created_at)` — supports
  monitoring queries like "list recent FAILED create-order sagas".

**Status semantics:**

| `status`       | Meaning                                                                            |
|----------------|------------------------------------------------------------------------------------|
| `STARTED`      | `begin` ran. May be the current state of a saga in flight, or an interrupted saga. |
| `COMPLETED`    | Every step succeeded.                                                              |
| `COMPENSATING` | A step failed; compensations are running. Caller has not yet observed the failure. |
| `COMPENSATED`  | Compensations finished without throwing.                                           |
| `FAILED`       | At least one compensation itself threw — operator attention required.              |

**Delivery semantics & idempotency:**

- The orchestrator runs **in-process and synchronously**. A process crash mid-saga
  leaves the row in `STARTED` or `COMPENSATING`; there is currently no recovery
  job that resumes such sagas. They must be reconciled manually (typically the
  outbox release event from a partially-completed step will have been committed
  to `outbox_events` and will be sent by `OutboxPoller` regardless).
- Compensations should be **idempotent** — see the per-step notes above. The
  outbox-based release on `ReserveStockStep.compensate` is idempotent end-to-end
  because inventory-service treats `release` by SKU + quantity, and the saga
  records a fresh `aggregate_id` (the saga UUID) per attempt so it can't
  collide with a real order's release.

**Multi-instance coordination:**

- Each saga instance lives within a single JVM. There is no cross-instance
  hand-off, so concurrency between instances is a non-issue. Different orders
  run on whichever instance receives the HTTP request.
- The `saga_instance` PK is a fresh UUID per call, so two instances cannot
  collide on a row.

**Configuration:**

- No `saga.*` configuration properties exist today. The shipping cost (`100`),
  saga type (`"create-order"`), outbox aggregate type (`"CreateOrderSaga"`), and
  the inventory/payment service URLs are hardcoded constants in the step classes.
  If they need to vary, externalize through Config Server.
- The order total charged by `ProcessPaymentStep` is
  `Σ(unitPrice × quantity) + shippingCost + taxAmount`. `taxAmount` is currently
  `0`, so it is effectively items + shipping.

<a id="payment-failed-reaper"></a>**PAYMENT_FAILED reaper:**

`PaymentFailedReaper` (`@Scheduled`, package `orderservice.managers`) prevents stock from
leaking on abandoned `PAYMENT_FAILED` orders. A declined payment leaves the order in
`PAYMENT_FAILED` with its stock **still reserved** (for retry); if the customer never
retries, that stock would be held forever.

- **Schedule:** `@Scheduled(cron = "${order.payment-failed.cleanup-cron:0 0 * * * *}")`
  — top of every hour by default.
- **Logic:** loads order ids where `orderStatus = PAYMENT_FAILED` and
  `createdAt < now − retentionHours` (via `OrderRepository.findIdsByStatusAndCreatedBefore`),
  then calls `orderManager.changeOrderStatus(id, CANCELLED)` for each. That existing path
  publishes a `"release"` outbox event, so inventory-service frees the held stock.
- **Timestamp caveat:** it uses `Order.createdAt` (there is no per-status timestamp). Since
  payment failure happens within seconds of creation, "created > N hours ago and still
  `PAYMENT_FAILED`" is a good proxy for "stuck for N hours". A customer who retries inside
  the window simply keeps the order alive only until `createdAt + retentionHours`.
- **Multi-instance safe:** each instance runs the job, but `changeOrderStatus` takes a
  pessimistic lock and is a no-op once the order is already `CANCELLED`, so concurrent
  reapers can't double-release. Per-order failures are caught and logged so one bad row
  can't stall the batch.

Configuration (`order.payment-failed.*`):

- **`retention-hours`** (default `24`) — how long an order may stay `PAYMENT_FAILED`
  before auto-cancellation.
- **`cleanup-cron`** (default `0 0 * * * *`) — reaper schedule.

`PaymentFailedReaperProperties` is registered via `@EnableConfigurationProperties` on
`OrderServiceApplication`. Neither property is set in `webstore-config` today — defaults
apply; override under `order:` in `C:\Projects\webstore-config\config\order-service.yml`.

### Configuration Management

The service uses Spring Cloud Config for externalized configuration:

- Config Server URI: `http://localhost:8071`
- Config source (Git, local clone): **`C:\Projects\webstore-config`**
    - Shared defaults: `config/application.yml`
    - Order-service overrides: `config/order-service.yml`
- `application.yml` (in this service's source tree) only contains bootstrap config
  (application name + `config.import: optional:configserver:`)

**Order-service defaults pulled from the config repo:**

- `server.port`: **8077** (from `order-service.yml`)
- `service.schemaName`: **`order_schema`** — injected into the shared datasource URL
  (`jdbc:postgresql://localhost:5432/webstore?currentSchema=order_schema`)
- `topic.stock.status`: **`stock-status-event`** (from `application.yml`)
- `topic.order.status`: **`order-status-event`** (from `application.yml`)
- `num.partitions`: **12**, `replication.factor`: **3**
- `bootstrap.servers`: `localhost:9092`
- Eureka registry: `http://localhost:8070/eureka/`

> Property names in code use the path form (`topic.stock.status`); the topic **value** that actually
> lands on the wire is `stock-status-event`. The two are easy to confuse when grepping.

To change any of the above, edit the file under `C:\Projects\webstore-config\config\`, commit, and push —
the Config Server reads from Git, not the local working copy, so an un-pushed change won't take effect.

### Database Schema

**Tables:**

- `orders` — main order table with embedded address columns (`country`, `region`, `city`, `street`,
  `address_line`), `customer_id`, `created_at`, `tax_amount`, `shipping_cost`, `order_status`, `version`
- `order_item` — line items with FK to `orders(id)` and unique constraint
  `uc_orderitem_order_id (order_id, product_sku)`; columns: `product_sku`, `unit_price`, `quantity`,
  `product_name`, `version`
- `outbox_events` — transactional outbox rows. Columns: `id UUID PK`, `version`, `aggregate_type`,
  `aggregate_id`, `event_type`, `topic_name`, `payload TEXT`, `status` (`PENDING` /
  `PROCESSING` / `SENT` / `FAILED`, default `'PENDING'`), `created_at`, `processed_at`. Two
  partial indexes: `idx_outbox_status_created` filtered on `status='PENDING'` (poller query),
  `idx_outbox_processing` filtered on `status='PROCESSING'` (recovery query).
- `inbox_messages` — transactional inbox rows. Columns: `message_id VARCHAR(255) PK`,
  `version`, `aggregate_type`, `aggregate_id` (nullable), `event_type`, `topic_name`,
  `partition_no`, `kafka_offset`, `payload TEXT`, `status` (`RECEIVED` / `PROCESSED` /
  `FAILED`, default `'RECEIVED'`), `received_at`, `processed_at`. One partial index
  `idx_inbox_status_processed_at` filtered on `status='PROCESSED'` (cleanup query) plus a
  secondary `idx_inbox_topic_aggregate (topic_name, aggregate_id)` for debugging lookups.
- `saga_instance` — orchestration-saga audit/state rows. Columns: `id UUID PK`,
  `version`, `saga_type`, `current_step` (nullable), `status` (`STARTED` /
  `COMPLETED` / `COMPENSATING` / `COMPENSATED` / `FAILED`, default `'STARTED'`),
  `error_message TEXT`, `created_at`, `updated_at`. One composite index
  `idx_saga_type_status_created (saga_type, status, created_at)` for monitoring
  queries (e.g. "recent FAILED create-order sagas").

**Sequences:**

- `orders_seq` (start 1, increment 50)
- `order_item_seq` (start 1, increment 50)
- (outbox uses `GenerationType.UUID`, not a DB sequence)
- (inbox uses the producer-supplied `messageId` String as its PK; no DB sequence)
- (saga uses `GenerationType.UUID`, not a DB sequence)

**Flyway Migrations:**

- Located in `src/main/resources/db/migration/`
- `V1__init_tables.sql` — initial `orders` / `order_item` schema
- `V2__create_outbox_table.sql` — `outbox_events` table and partial indexes
- `V3__create_inbox_table.sql` — `inbox_messages` table with partial cleanup index and
  topic/aggregate lookup index
- `V4__create_saga_instance_table.sql` — `saga_instance` table and the
  `(saga_type, status, created_at)` index

### Order Lifecycle & Status Flow

**`OrderStatus` is a state machine** (declared in reverse for forward-reference reasons):

```
NEW ──────► COMPLETED ──► REFUNDED
 ├────────► CANCELLED
 └────────► PAYMENT_FAILED ──► COMPLETED   (retry succeeds)
                  └──────────► CANCELLED   (customer gives up)
```

- `NEW` → `COMPLETED`, `CANCELLED`, or `PAYMENT_FAILED`
- `PAYMENT_FAILED` → `COMPLETED` (successful retry) or `CANCELLED` (abandon)
- `COMPLETED` → `REFUNDED`
- `CANCELLED` and `REFUNDED` are terminal
- Invalid transitions throw `IllegalArgumentException` from `Order.setOrderStatus`

> `PAYMENT_FAILED` is a **retry-able** state set by `ProcessPaymentStep` when payment-service
> *declines* a charge during order creation. The reserved stock is **kept** while in this state
> so the customer can re-attempt payment via `POST /v1/orders/{orderId}/retry-payment`
> (`OrderManager.retryPayment`). The enum is `@Enumerated(EnumType.STRING)`, so adding the value
> needed **no Flyway migration**.
>
> Because the held stock would otherwise leak, `PaymentFailedReaper` (a `@Scheduled` job)
> auto-cancels orders left in `PAYMENT_FAILED` longer than
> `order.payment-failed.retention-hours` (default 24h) — see the
> [PAYMENT_FAILED reaper](#payment-failed-reaper) section.

**Status-change side effects (in `OrderManager.changeOrderStatus`):**

| New status       | Outbox event (`event_type`) → eventually published on `${topic.stock.status}` |
|------------------|-------------------------------------------------------------------------------|
| `CANCELLED`      | `"release"`                                                                   |
| `REFUNDED`       | `"release"`                                                                   |
| `COMPLETED`      | `"commit"`                                                                    |
| `NEW`            | none                                                                          |
| `PAYMENT_FAILED` | none (set by the saga, not via `changeOrderStatus`; stock stays reserved)     |

> The retry-success path is `PAYMENT_FAILED → COMPLETED` driven by the async payment event →
> emits `"commit"` (the held stock is committed). The abandon path is `PAYMENT_FAILED → CANCELLED`
> → emits `"release"` (the held stock is freed). Both reuse the existing `changeOrderStatus` logic.

The outbox row and the order update commit atomically. The Kafka send happens asynchronously
via `OutboxPoller` / `OutboxEventProcessor`. If the new status equals the current status, the
method is a no-op (no DB write, no outbox row).

### Business Logic Notes

**`OrderManager.createOrder(CreateOrderDto)`**

This method is a thin delegate: it returns
`createOrderSaga.execute(orderDto)` directly. All of the actual logic lives in
`CreateOrderSaga` and its four steps; see the [Orchestration Saga](#orchestration-saga)
section for the full design. Summary of what the saga does:

1. `CreateOrderSaga.execute` null-checks the DTO and runs `baseValidator.validate(orderDto)`
   plus `orderItemValidator.validate(orderDto.getOrderItems())` (rejects duplicate `productSKU`).
2. `SagaOrchestrator.execute("create-order", ...)` inserts a `saga_instance` row
   (status `STARTED`) in its own transaction and stamps the `sagaId` onto the
   `SagaContext`.
3. **`PriceItemsStep`** — POSTs SKUs to `/v1/inventory/prices` and stores the
   SKU → unit-price map in the context. Read-only; no compensation.
4. **`ReserveStockStep`** — POSTs `List<InventoryDto>` (sku + quantity) to
   `/v1/inventory/reserve-stock`. On 4xx → `NotEnoughStockException`; on 5xx
   → `IllegalStateException`. Compensation publishes a `"release"` event to the
   outbox using the **saga id** as the aggregate id (no order id exists yet).
5. **`PersistOrderStep`** — inside an injected `TransactionTemplate`, builds the
   `Order` (status `NEW`, hardcoded `shippingCost = 100`, mapped `Address`,
   `customerId`), attaches each `OrderItem` with `unitPrice` from the
   context-stashed price map, and `orderRepository.save(...)`. Compensation
   reloads the order and sets it to `CANCELLED` (no stock event — that's owned by
   `ReserveStockStep`).
6. **`ProcessPaymentStep`** — charges via `PaymentClient`
   (`POST payment-service/v1/payments`). **Declined** payment (200 + `FAILED`) → order
   set to `PAYMENT_FAILED`, stock kept, no compensation. **Transport error** (4xx →
   `PaymentFailedException`, 5xx → `IllegalStateException`) → saga failure → compensation.
   On success the order is left `NEW`; payment-service's async `OrderStatusKafka` event
   drives `NEW → COMPLETED`. Final step; no compensation override.
7. On success (incl. a declined payment), the orchestrator marks the saga `COMPLETED`
   and `CreateOrderSaga` returns the saved `Order` (`NEW` on success, `PAYMENT_FAILED`
   on decline) from the context. On a thrown step, executed steps are compensated in
   reverse order; the orchestrator wraps the original exception in a
   `SagaExecutionException` (its cause is e.g. `NotEnoughStockException` or
   `PaymentFailedException`).

> Note: `taxAmount` is **not** calculated by the saga; it defaults to `BigDecimal.ZERO` in
> the entity. `shippingCost` is the only fee applied, and it is hardcoded at `100` (in
> `PersistOrderStep`). If these need to vary, externalize via Config Server properties.

**`OrderManager.retryPayment(Long orderId)`**

Re-attempts payment for an order a declined charge left in `PAYMENT_FAILED` (the reserved
stock is still held, so no re-reservation is done — this only re-charges).

1. Null-check `orderId`; load via `orderRepository.findByIdWithItems` (join-fetches items
   so the total can be computed outside a session); 404 if absent.
2. Reject unless `orderStatus == PAYMENT_FAILED` (`IllegalArgumentException` → 400).
3. `paymentClient.charge(order)` re-POSTs to payment-service, which **updates the existing
   payment row** for the order (see payment-service note) rather than inserting a duplicate.
4. The `PAYMENT_FAILED → COMPLETED` transition is **async**: on a successful charge
   payment-service emits an `OrderStatusKafka` event the consumer turns into `COMPLETED`.
   The returned `Order` therefore still reads `PAYMENT_FAILED` until that event lands; a
   repeated decline leaves it `PAYMENT_FAILED` for another attempt.

> Not `@Transactional` — it reads the order (items fetched eagerly via `findByIdWithItems`)
> and makes an external REST call; it performs no DB write itself.

**`OrderManager.changeOrderStatus(orderId, newOrderStatus)`**

1. Null-check both arguments
2. Load order with `findByIdForUpdate` (pessimistic write lock); throw `EntityNotFoundException` if absent
3. If new status == old status, log and return (no save, no outbox row)
4. Call `order.setOrderStatus(newOrderStatus)` — entity validates the transition
5. Determine action type: `CANCELLED`/`REFUNDED` → `"release"`, `COMPLETED` → `"commit"`
6. If action type was assigned, call `outboxPublisher.publishOrderItemEvent(orderId, actionType,
   orderItemMapper.toDto(order.getItems()))` — inserts an outbox row in the **same** transaction
7. `orderRepository.save(order)` — the outbox row and order update commit together

**`OrderManager.addItemsToOrder(orderId, List<OrderItemDto>)`**

1. Null-check `orderId`; validate items list (incl. duplicate-SKU check)
2. Load order with `findByIdForUpdate`; throw `EntityNotFoundException` if absent
3. `addItems(...)` — same pricing + stock-reservation flow as `createOrder`
4. `orderRepository.save(order)`

> `Order.addItem` enforces `orderStatus == NEW`; otherwise `IllegalArgumentException`.

**`OrderManager.removeOrderItem(orderId, orderItemId)`**

1. Null-check both arguments
2. Load `OrderItem` via `findByIdAndOrderId` (404 if not found)
3. Load `Order` via `findByIdForUpdate` (404 if not found)
4. `order.removeItem(item)` — enforces `orderStatus == NEW`
5. `orderRepository.save(order)` — orphan removal deletes the item row
6. `outboxPublisher.publishOrderItemEvent(orderId, "release", List.of(itemDto))` — inserts a
   `"release"` outbox row for the removed item, in the **same** transaction

**Read methods (`@Transactional(readOnly = true)`):**

- `getOrderById(Long)` — single order; 404 if absent
- `getOrderByCustomerId(Long)` — list of customer's orders
- `getItemsByOrderId(Long)` — all items for an order (via `OrderItemRepository.findAllByOrderId`)
- `getOrderItem(Long)` — single item by id; 404 if absent

### API Endpoints

Base path: `/v1/orders`

| Method | Path                                  | Request Body         | Response             | Description                  |
|--------|---------------------------------------|----------------------|----------------------|------------------------------|
| POST   | `/v1/orders`                          | `CreateOrderDto`     | 200 OK + `OrderDto`  | Create new order             |
| GET    | `/v1/orders/{orderId}`                | -                    | `OrderDto`           | Get order by ID              |
| GET    | `/v1/orders/customer/{customerId}`    | -                    | `List<OrderDto>`     | Get all orders for customer  |
| PUT    | `/v1/orders/{orderId}/{status}`       | -                    | 204 No Content       | Update order status          |
| POST   | `/v1/orders/{orderId}/retry-payment`  | -                    | 200 OK + `OrderDto`  | Retry a declined payment     |
| GET    | `/v1/orders/{orderId}/items`          | -                    | `List<OrderItemDto>` | List items on an order       |
| GET    | `/v1/orders/{orderId}/items/{itemID}` | -                    | `OrderItemDto`       | Get a single order item      |
| POST   | `/v1/orders/{orderId}/items`          | `List<OrderItemDto>` | 204 No Content       | Add items to existing order  |
| DELETE | `/v1/orders/{orderId}/items/{itemID}` | -                    | 204 No Content       | Remove an item from an order |

> `POST /v1/orders/{orderId}/retry-payment` requires the order to be in `PAYMENT_FAILED`
> (else 400). It returns the order (still `PAYMENT_FAILED` until the async success event
> lands — see `OrderManager.retryPayment`).

The `POST /v1/orders` and `POST /v1/orders/{orderId}/items` endpoints apply `@Valid` to their request bodies,
triggering Bean Validation before reaching `OrderManager`.

**Exception Handling (`RestExceptionHandler`):**

| Exception                         | HTTP status               |
|-----------------------------------|---------------------------|
| `IllegalArgumentException`        | 400 Bad Request           |
| `NotEnoughStockException`         | 400 Bad Request           |
| `PaymentFailedException`          | 402 Payment Required      |
| `EntityNotFoundException`         | 404 Not Found             |
| `DataIntegrityViolationException` | 409 Conflict              |
| `NullPointerException`            | 500 Internal Server Error |

> A `SagaExecutionException` from a failed create-order saga is **not** mapped directly — the
> orchestrator preserves the original exception as its cause, so the cause (e.g.
> `NotEnoughStockException`, `PaymentFailedException`) is what surfaces. Spring's handler resolution
> unwraps the cause to match these `@ExceptionHandler`s.

### Important Implementation Details

**When adding new outbound (produced) Kafka event types:**

1. Create the payload DTO in `orderservice.dto.kafka` (or reuse an existing DTO — it's just
   what gets JSON-serialized into the `payload` column).
2. From inside a `@Transactional` business method on a manager, call
   `outboxPublisher.publish(aggregateType, aggregateId, eventType, topicName, payload)`. Do
   **not** call `KafkaTemplate` directly — that re-opens the dual-write window the outbox
   exists to close.
3. If the new event has its own topic, add a `NewTopic` bean in `KafkaConfig` so it is
   auto-created on startup.
4. Make sure the consumer side is idempotent (see "Delivery semantics & idempotency" in the
   [Outbox Pattern](#outbox-pattern) section).

**When adding new inbound (consumed) Kafka event types:**

1. Create the DTO in `orderservice.dto.kafka`.
2. Add a `ConsumerFactory` + `ConcurrentKafkaListenerContainerFactory` bean in `KafkaConfig`.
3. Add a `@KafkaListener`-annotated method on a `@Service`, annotated `@Transactional` (DB tx);
   the existing `RECORD` ack mode + `enable.auto.commit=false` will commit offsets inside the tx.
4. Validate input (null checks, unknown `actionType` etc. — follow `KafkaConsumerService` for
   the pattern).
5. **Funnel the event through `InboxProcessor.processOnce`** for dedup:
    - Take `ConsumerRecord<K, V>` (not just the payload) and a
      `@Header(name = "X-Message-Id", required = false) String messageIdHeader` argument.
    - Build the `InboxMessage` via `inboxProcessor.fromKafkaRecord(messageId, aggregateType,
      aggregateId, eventType, record, payload)`.
    - Wrap the business call in `inboxProcessor.processOnce(msg, () -> ...handler...)`.
    - Compute `messageId` as: header value (via `StringUtils.hasText`) → otherwise a stable
      business key. **Never** use `topic-partition-offset`.
6. Make sure the upstream producer stamps the `X-Message-Id` header. If it can't (yet),
   ensure your business-key fallback can never collide with a legitimate second event for
   the same key.

**When modifying entities:**

1. Create a new Flyway migration (`V{next}__description.sql`) — never edit existing migrations
2. Update the entity class
3. Update the corresponding DTO
4. Update the MapStruct mapper if field-mapping changes
5. Consider `@Version` (optimistic) and `findByIdForUpdate` (pessimistic) implications for concurrent paths

**When adding new REST endpoints:**

1. Add a method to `OrderController`
2. Implement business logic in `OrderManager` (or a dedicated manager if cross-cutting)
3. Add validation via `@Valid` on request bodies and/or `BaseValidator` / `OrderItemValidator` calls
4. Add new exception types to `RestExceptionHandler` as needed
5. Follow existing transactional and locking patterns (`@Transactional`, `findByIdForUpdate` on write paths)

**When integrating with another service over REST:**

1. Use the injected `RestClient` (already `@LoadBalanced` via Eureka)
2. Reference the target service by Eureka name (`http://inventory-service/...`)
3. Add explicit `onStatus` handlers for 4xx and 5xx — map to domain exceptions handled by
   `RestExceptionHandler` (`NotEnoughStockException`, `IllegalArgumentException`, `IllegalStateException`)

**When adding a new orchestration saga:**

1. For each forward action that may need a rollback, implement a `SagaStep` in
   a sub-package of `orderservice.saga` (mirror `saga.createorder`). Override
   `compensate(SagaContext)` only when an undo is actually required — the
   default no-op is correct for read-only or terminal steps.
2. Add a `@Component` saga class (analogous to `CreateOrderSaga`) that builds
   the ordered `List<SagaStep>`, prepares a `SagaContext` with any input the
   first step needs, and calls
   `orchestrator.execute("<saga-type>", steps, context)`.
3. Steps that must commit their own work (or write outbox rows in compensation)
   should take a `TransactionTemplate` and wrap the work in
   `transactionTemplate.execute(...)` — the orchestrator does **not** open a
   transaction around steps. The constructor injects a `PlatformTransactionManager`
   and wraps it once.
4. Share data across steps via `SagaContext.put` / `SagaContext.get(key, Class)`.
   Use the `sagaId` (auto-populated by the orchestrator after `begin`) as a
   stable identifier when the entity hasn't been persisted yet — e.g. as an
   outbox `aggregate_id` in compensation.
5. Compensations must be **idempotent** — they may be invoked even when the
   forward step failed before producing visible side effects (the orchestrator
   can't tell the difference). Prefer set-based / by-key operations.
6. Callers see `SagaExecutionException` on failure; its `getCause()` is the
   original step exception (e.g. `NotEnoughStockException`), which is what
   propagates through `RestExceptionHandler` if not handled specifically.

### Testing Notes

Current test coverage is minimal (only context-load test exists). When adding tests:

- Unit tests should mock `OrderRepository`, `OrderItemRepository`, `RestClient`, `OutboxPublisher`,
  `InboxProcessor`, `SagaOrchestrator` / `SagaStateService`, and (for the lower-level paths)
  `OutboxEventRepository` / `InboxMessageRepository` / `SagaInstanceRepository` + `KafkaTemplate`.
- Integration tests should use `@SpringBootTest` with Testcontainers for PostgreSQL and Kafka.
- Use `@Transactional` on test methods for automatic rollback.
- Pay particular attention to: state-machine transitions in `OrderStatus`, duplicate-SKU rejection,
  pessimistic-lock contention paths, 4xx/5xx fan-out from the inventory-service REST calls,
  the **outbox happy path + failure modes** (claim race between two instances, send failure →
  `markPendingForRetry`, stuck-event recovery, cleanup of SENT rows), the
  **inbox happy path + failure modes** (first-time message → handler runs and row marked
  PROCESSED, redelivered message → handler skipped, concurrent insert race → one wins via
  `DataIntegrityViolationException`, header-present vs business-key fallback paths, cleanup of
  PROCESSED rows), and the **saga happy path + failure modes**
  (all steps succeed → `saga_instance.status = COMPLETED`; mid-saga step throws →
  executed steps' compensations run in reverse order, status becomes `COMPENSATED`,
  caller sees `SagaExecutionException` with original exception as cause; compensation
  itself throws → status becomes `FAILED`; `ReserveStockStep` compensation inserts an
  outbox `release` row keyed on the saga UUID). For the **payment step** specifically,
  cover all three outcomes (mock the payment-service `RestClient` / `PaymentClient`):
    - **Completed** (200 + `COMPLETED`): order stays `NEW`, saga `COMPLETED`, **no**
      compensation, **no** `release` outbox row.
    - **Declined** (200 + `FAILED`): order ends `PAYMENT_FAILED`, stock **kept** (no
      `release` outbox row), saga still `COMPLETED` (a decline is not a saga failure).
    - **Transport error** (4xx → `PaymentFailedException`, 5xx → `IllegalStateException`):
      compensations run — order `CANCELLED`, exactly one `release` outbox row.
      Also test `retryPayment`: rejects a non-`PAYMENT_FAILED` order (400); a declined retry
      leaves it `PAYMENT_FAILED`; and on payment-service, a re-attempt **updates** the existing
      payment row rather than inserting (no `uc_payment_order` violation).
- `PaymentFailedReaper`: an order `PAYMENT_FAILED` with `createdAt` older than
  `retention-hours` is moved to `CANCELLED` (assert a `release` outbox row is written); one
  inside the window is left untouched; and a `changeOrderStatus` failure on one id doesn't
  abort the batch.
- `OutboxPublisher.publish` and `InboxProcessor.processOnce` / `.recordIfNew` use
  `Propagation.MANDATORY`; tests calling them directly must wrap the call in
  `TransactionTemplate` / `@Transactional` or they will throw `IllegalTransactionStateException`.
- `SagaStateService` methods use `Propagation.REQUIRES_NEW`; in tests where the outer
  `@Transactional` rolls back, the `saga_instance` row is still committed and visible —
  useful for asserting lifecycle transitions, but it means tests must clean up the table
  explicitly if they care about isolation.

### Dependencies to Be Aware Of

- **MapStruct 1.5.5.Final** — compile-time code generation for mappers
- **Lombok** — annotation processor required for IDE compilation
- **Flyway 10.20.0** — runs migrations on startup
- **Spring Cloud (2024.0.1 / 2025.0.0)** — Eureka client, Config client, LoadBalancer
- **Spring Kafka** — idempotent producer + consumer (no Kafka transactions; outbox replaces them)
- **Spring `@Scheduled`** — drives `OutboxPoller`, recovery, `OutboxCleaner`,
  `InboxCleaner`, and `PaymentFailedReaper` (enabled by `@EnableScheduling` on
  `OrderServiceApplication`)
- **Spring `TransactionTemplate`** — used by saga steps (`PersistOrderStep`,
  `ReserveStockStep.compensate`) to open their own DB transactions without relying
  on an enclosing `@Transactional` boundary; `SagaStateService` uses
  `Propagation.REQUIRES_NEW` to commit lifecycle rows independently
- **Jackson `ObjectMapper`** — serializes outbox **and inbox** payloads to JSON
- **PostgreSQL JDBC** — runtime dependency
- **Bean Validation (Jakarta)** — DTO and entity constraints

### Service Dependencies

This service requires the following to be running:

1. **Config Server** (localhost:8071) — for configuration
2. **Eureka Server** (localhost:8761) — for service discovery
3. **PostgreSQL 17** — for data persistence
4. **Kafka broker** — for event streaming
5. **inventory-service** — for synchronous price lookup and stock reservation during order creation /
   item addition (must be registered with Eureka)
6. **payment-service** — called **synchronously** via `PaymentClient` during order creation
   (`ProcessPaymentStep`) and on payment retry (`OrderManager.retryPayment`)
   (`POST /v1/payments`, must be registered with Eureka); also **produces** `OrderStatusKafka` events
   that asynchronously drive status transitions to `COMPLETED` / `REFUNDED`
