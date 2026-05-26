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

- `OrderManager` (`@Service`) — core order/order-item business logic and persistence. Writes
  outbound events to the outbox **in the same DB transaction** as the order mutation, via
  `OutboxPublisher`.
- `KafkaConsumerService` (`@Service`) — inbound order-status (payment) events. Every
  handled event is funneled through `InboxProcessor.processOnce` for structural
  exactly-once processing.

Outbox infrastructure (package `orderservice.outbox`) handles outbound publishing
asynchronously — see the [Outbox Pattern](#outbox-pattern) section. Inbox
infrastructure (package `orderservice.inbox`) handles consumer-side dedup — see
the [Inbox Pattern](#inbox-pattern) section.

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

### Inter-Service Communication

**Synchronous (REST) — outbound to inventory-service:**

Uses a Eureka-aware `@LoadBalanced` `RestClient` from `RestConfig`. Two integration points exist in
`OrderManager`:

| Call                                                       | Triggered by                                                 | Failure → exception                                             |
|------------------------------------------------------------|--------------------------------------------------------------|-----------------------------------------------------------------|
| `POST http://inventory-service/v1/inventory/prices`        | `addItems` (called from `createOrder` and `addItemsToOrder`) | 4xx → `IllegalArgumentException`, 5xx → `IllegalStateException` |
| `POST http://inventory-service/v1/inventory/reserve-stock` | `addItems` (called from `createOrder` and `addItemsToOrder`) | 4xx → `NotEnoughStockException`, 5xx → `IllegalStateException`  |

Both calls send/receive `List<InventoryDto>` payloads. `InventoryDto` carries `productSKU`, `stockLevel`,
`reservedStock`, `stockPrice`, `sellPrice`, and `measurementUnit`.

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

**Sequences:**

- `orders_seq` (start 1, increment 50)
- `order_item_seq` (start 1, increment 50)
- (outbox uses `GenerationType.UUID`, not a DB sequence)
- (inbox uses the producer-supplied `messageId` String as its PK; no DB sequence)

**Flyway Migrations:**

- Located in `src/main/resources/db/migration/`
- `V1__init_tables.sql` — initial `orders` / `order_item` schema
- `V2__create_outbox_table.sql` — `outbox_events` table and partial indexes
- `V3__create_inbox_table.sql` — `inbox_messages` table with partial cleanup index and
  topic/aggregate lookup index

### Order Lifecycle & Status Flow

**`OrderStatus` is a state machine** (declared in reverse for forward-reference reasons):

```
NEW ──► COMPLETED ──► REFUNDED
 └────► CANCELLED
```

- `NEW` → `COMPLETED` or `CANCELLED`
- `COMPLETED` → `REFUNDED`
- `CANCELLED` and `REFUNDED` are terminal
- Invalid transitions throw `IllegalArgumentException` from `Order.setOrderStatus`

**Status-change side effects (in `OrderManager.changeOrderStatus`):**

| New status  | Outbox event (`event_type`) → eventually published on `${topic.stock.status}` |
|-------------|-------------------------------------------------------------------------------|
| `CANCELLED` | `"release"`                                                                   |
| `REFUNDED`  | `"release"`                                                                   |
| `COMPLETED` | `"commit"`                                                                    |
| `NEW`       | none                                                                          |

The outbox row and the order update commit atomically. The Kafka send happens asynchronously
via `OutboxPoller` / `OutboxEventProcessor`. If the new status equals the current status, the
method is a no-op (no DB write, no outbox row).

### Business Logic Notes

**`OrderManager.createOrder(CreateOrderDto)`**

1. Null-check the DTO
2. `baseValidator.validate(orderDto)` — bean validation (cascades into address and items)
3. `orderItemValidator.validate(orderDto.getOrderItems())` — re-validates items and rejects duplicate
   `productSKU`
4. Build new `Order` with status `NEW`, `shippingCost = 100` (hardcoded in `getShippingCost`), mapped
   `Address`, and `customerId`
5. `addItems(newOrder, dtos)` — for each item: map DTO → entity, set `unitPrice` from the
   inventory-service price lookup (`/v1/inventory/prices`), attach via `Order.addItem`
6. After items are attached, `addItems` calls `reserveStock` which POSTs to
   `/v1/inventory/reserve-stock` — a 4xx response raises `NotEnoughStockException`
7. `orderRepository.save(newOrder)`

> Note: `taxAmount` is **not** calculated by `createOrder`; it defaults to `BigDecimal.ZERO` in the entity.
> `shippingCost` is the only fee applied, and it is hardcoded at `100`. If these need to vary, externalize
> via Config Server properties.

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
| GET    | `/v1/orders/{orderId}/items`          | -                    | `List<OrderItemDto>` | List items on an order       |
| GET    | `/v1/orders/{orderId}/items/{itemID}` | -                    | `OrderItemDto`       | Get a single order item      |
| POST   | `/v1/orders/{orderId}/items`          | `List<OrderItemDto>` | 204 No Content       | Add items to existing order  |
| DELETE | `/v1/orders/{orderId}/items/{itemID}` | -                    | 204 No Content       | Remove an item from an order |

The `POST /v1/orders` and `POST /v1/orders/{orderId}/items` endpoints apply `@Valid` to their request bodies,
triggering Bean Validation before reaching `OrderManager`.

**Exception Handling (`RestExceptionHandler`):**

| Exception                         | HTTP status               |
|-----------------------------------|---------------------------|
| `IllegalArgumentException`        | 400 Bad Request           |
| `NotEnoughStockException`         | 400 Bad Request           |
| `EntityNotFoundException`         | 404 Not Found             |
| `DataIntegrityViolationException` | 409 Conflict              |
| `NullPointerException`            | 500 Internal Server Error |

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

### Testing Notes

Current test coverage is minimal (only context-load test exists). When adding tests:

- Unit tests should mock `OrderRepository`, `OrderItemRepository`, `RestClient`, `OutboxPublisher`,
  `InboxProcessor`, and (for the lower-level paths) `OutboxEventRepository` /
  `InboxMessageRepository` + `KafkaTemplate`.
- Integration tests should use `@SpringBootTest` with Testcontainers for PostgreSQL and Kafka.
- Use `@Transactional` on test methods for automatic rollback.
- Pay particular attention to: state-machine transitions in `OrderStatus`, duplicate-SKU rejection,
  pessimistic-lock contention paths, 4xx/5xx fan-out from the inventory-service REST calls,
  the **outbox happy path + failure modes** (claim race between two instances, send failure →
  `markPendingForRetry`, stuck-event recovery, cleanup of SENT rows), and the
  **inbox happy path + failure modes** (first-time message → handler runs and row marked
  PROCESSED, redelivered message → handler skipped, concurrent insert race → one wins via
  `DataIntegrityViolationException`, header-present vs business-key fallback paths, cleanup of
  PROCESSED rows).
- `OutboxPublisher.publish` and `InboxProcessor.processOnce` / `.recordIfNew` use
  `Propagation.MANDATORY`; tests calling them directly must wrap the call in
  `TransactionTemplate` / `@Transactional` or they will throw `IllegalTransactionStateException`.

### Dependencies to Be Aware Of

- **MapStruct 1.5.5.Final** — compile-time code generation for mappers
- **Lombok** — annotation processor required for IDE compilation
- **Flyway 10.20.0** — runs migrations on startup
- **Spring Cloud (2024.0.1 / 2025.0.0)** — Eureka client, Config client, LoadBalancer
- **Spring Kafka** — idempotent producer + consumer (no Kafka transactions; outbox replaces them)
- **Spring `@Scheduled`** — drives `OutboxPoller`, recovery, `OutboxCleaner`, and
  `InboxCleaner` (enabled by `@EnableScheduling` on `OrderServiceApplication`)
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
6. **payment-service** — produces `OrderStatusKafka` events that drive status transitions to
   `COMPLETED` / `REFUNDED`
