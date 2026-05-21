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
- Reacting to payment lifecycle events from payment-service (Kafka)

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
- `KafkaConsumerService` (`@Service`) — inbound order-status (payment) events.

Outbox infrastructure (package `orderservice.outbox`) handles outbound publishing
asynchronously — see the [Outbox Pattern](#outbox-pattern) section.

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

**6. Transactional Outbox (outbound Kafka)**

- Replaces the previous Kafka-transactional producer pattern. See the
  [Outbox Pattern](#outbox-pattern) section for the full design.

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
- **Delivery semantics:** at-least-once on the producer side (consumers must be idempotent). The
  consumer side uses `read_committed` + manual offset commit inside a DB transaction.

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

- Ignores events with `null` orderId (logs warn, returns)
- Maps `actionType`: `"Completed"` → `OrderStatus.COMPLETED`, `"Refunded"` → `OrderStatus.REFUNDED`
- Any other `actionType` is logged and ignored
- Delegates to `OrderManager.changeOrderStatus`

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

**Sequences:**

- `orders_seq` (start 1, increment 50)
- `order_item_seq` (start 1, increment 50)
- (outbox uses `GenerationType.UUID`, not a DB sequence)

**Flyway Migrations:**

- Located in `src/main/resources/db/migration/`
- `V1__init_tables.sql` — initial `orders` / `order_item` schema
- `V2__create_outbox_table.sql` — `outbox_events` table and partial indexes

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
  and (for the processor) `OutboxEventRepository` + `KafkaTemplate`.
- Integration tests should use `@SpringBootTest` with Testcontainers for PostgreSQL and Kafka.
- Use `@Transactional` on test methods for automatic rollback.
- Pay particular attention to: state-machine transitions in `OrderStatus`, duplicate-SKU rejection,
  pessimistic-lock contention paths, 4xx/5xx fan-out from the inventory-service REST calls, and
  the **outbox happy path + failure modes** (claim race between two instances, send failure →
  `markPendingForRetry`, stuck-event recovery, cleanup of SENT rows).
- `OutboxPublisher.publish` uses `Propagation.MANDATORY`; tests calling it directly must wrap
  the call in `TransactionTemplate` / `@Transactional` or it will throw
  `IllegalTransactionStateException`.

### Dependencies to Be Aware Of

- **MapStruct 1.5.5.Final** — compile-time code generation for mappers
- **Lombok** — annotation processor required for IDE compilation
- **Flyway 10.20.0** — runs migrations on startup
- **Spring Cloud (2024.0.1 / 2025.0.0)** — Eureka client, Config client, LoadBalancer
- **Spring Kafka** — idempotent producer + consumer (no Kafka transactions; outbox replaces them)
- **Spring `@Scheduled`** — drives `OutboxPoller`, recovery, and `OutboxCleaner`
  (enabled by `@EnableScheduling` on `OrderServiceApplication`)
- **Jackson `ObjectMapper`** — serializes outbox payloads to JSON
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
