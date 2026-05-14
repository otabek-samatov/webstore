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
- Publishing stock lifecycle events to inventory-service (Kafka)
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

Manager layer is split into three `@Service` beans:

- `OrderManager` — core order/order-item business logic and persistence
- `KafkaProducerService` — outbound stock-status events
- `KafkaConsumerService` — inbound order-status (payment) events

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
- `KafkaProducerService.sendStockStatus` uses `@Transactional("kafkaTransactionManager")` for Kafka-side
  transactional sends

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

- **Produces:** `StockStatusKafka` events on `${topic.stock.status}` (key: `"order-" + orderId`)
- **Consumes:** `OrderStatusKafka` events from `${topic.order.status}` (payment-service is the producer)
- Exactly-once semantics with transactional producer/consumer

### Kafka Integration Details

**Producer Configuration (`KafkaConfig`):**

- Transactional ID: `{spring.application.name}-tx-{server.port}` (unique per instance)
- `enable.idempotence=true`, `acks=all`, `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`
- Topic `${topic.stock.status}` is auto-created via `NewTopic` bean with `${num.partitions}` /
  `${replication.factor}` from Config Server
- Wrapped with a `KafkaTransactionManager` bean

**Producer Usage (`KafkaProducerService.sendStockStatus`):**

Builds a `StockStatusKafka` from `List<OrderItemDto>` (each maps to a `StockLevelDto`) and sends to
`${topic.stock.status}` with key `"order-" + orderId`. Action types in use:

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

- Each instance MUST run on a different port (e.g., 8080, 8081, 8082)
- This ensures unique Kafka transactional IDs per instance
- Without unique ports, producer fencing will occur

### Configuration Management

The service uses Spring Cloud Config for externalized configuration:

- Config Server URI: `http://localhost:8071`
- `application.yml` only contains bootstrap config (application name + config import)
- Runtime properties resolved from Config Server include: `bootstrap.servers`, `topic.stock.status`,
  `topic.order.status`, `num.partitions`, `replication.factor`, datasource, server port, etc.

### Database Schema

**Tables:**

- `orders` — main order table with embedded address columns (`country`, `region`, `city`, `street`,
  `address_line`), `customer_id`, `created_at`, `tax_amount`, `shipping_cost`, `order_status`, `version`
- `order_item` — line items with FK to `orders(id)` and unique constraint
  `uc_orderitem_order_id (order_id, product_sku)`; columns: `product_sku`, `unit_price`, `quantity`,
  `product_name`, `version`

**Sequences:**

- `orders_seq` (start 1, increment 50)
- `order_item_seq` (start 1, increment 50)

**Flyway Migrations:**

- Located in `src/main/resources/db/migration/`
- Initial schema: `V1__init_tables.sql`

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

| New status  | Kafka stock-status event |
|-------------|--------------------------|
| `CANCELLED` | `"release"`              |
| `REFUNDED`  | `"release"`              |
| `COMPLETED` | `"commit"`               |
| `NEW`       | none                     |

If the new status equals the current status, the method is a no-op (no DB write, no Kafka event).

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
3. If new status == old status, log and return (no save, no event)
4. Call `order.setOrderStatus(newOrderStatus)` — entity validates the transition
5. Determine action type: `CANCELLED`/`REFUNDED` → `"release"`, `COMPLETED` → `"commit"`
6. If action type was assigned, call `kafkaProducerService.sendStockStatus(...)`
7. `orderRepository.save(order)`

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
6. Publish `"release"` stock-status event for the removed item

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

**When adding new Kafka event types:**

1. Create DTO in `orderservice.dto.kafka` package
2. Add a consumer factory + container factory in `KafkaConfig` (if consuming) or extend producer wiring
3. Use `@Transactional` on consumer methods; use `@Transactional("kafkaTransactionManager")` on producer
   methods that need to participate in a Kafka transaction
4. Add null/validation checks in handlers (see `KafkaConsumerService` for the pattern)

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

- Unit tests should mock `OrderRepository`, `OrderItemRepository`, `RestClient`, and the Kafka services
- Integration tests should use `@SpringBootTest` with Testcontainers for PostgreSQL and Kafka
- Use `@Transactional` on test methods for automatic rollback
- Pay particular attention to: state-machine transitions in `OrderStatus`, duplicate-SKU rejection,
  pessimistic-lock contention paths, and 4xx/5xx fan-out from the inventory-service REST calls

### Dependencies to Be Aware Of

- **MapStruct 1.5.5.Final** — compile-time code generation for mappers
- **Lombok** — annotation processor required for IDE compilation
- **Flyway 10.20.0** — runs migrations on startup
- **Spring Cloud (2024.0.1 / 2025.0.0)** — Eureka client, Config client, LoadBalancer
- **Spring Kafka** — producer/consumer + transactional support
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
