# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Test

- Build the application: `./gradlew build`
- Run tests: `./gradlew test`
- Run single test class: `./gradlew test --tests InventoryServiceApplicationTests`
- Clean build: `./gradlew clean build`

### Running the Application

- Start the service: `./gradlew bootRun`
- The service runs on port 8074 (set by Config Server via `inventory-service.yml`; the source
  `application.yml` carries only bootstrap config, so without Config Server it would fall back to 8080)
- Health check available at: `http://localhost:8074/actuator/health`

### Database Operations

- Database migrations are handled by Flyway automatically on startup
- Migration files located in `src/main/resources/db/migration/`
- PostgreSQL database required (configured in application.yml)

## Architecture Overview

This is a Spring Boot microservice for inventory management within a webstore architecture.

### Core Components

**Inventory Management System**: stock moves through a reserve → commit / release lifecycle, plus a
`revert` for restoring physical stock. The `InventoryManager` operations (quantity source noted, since
`InventoryDto` carries both `stockLevel` and `reservedStock`):

- `reserveStock()` (private, via `reserveStocks(List)`): `reservedStock += qty` (qty from
  `dto.reservedStock`). Rejects with `NotEnoughStockException` if available (`stockLevel − reservedStock`)
  is insufficient. Records `ReasonType.RESERVE_STOCK`.
- `commitStock()`: finalizes a sale — `reservedStock −= qty` **and** `stockLevel −= qty` (qty from
  `dto.reservedStock`). `ReasonType.COMMIT_STOCK`.
- `releaseStock()`: frees a reservation — `reservedStock −= qty` (qty from `dto.reservedStock`).
  `ReasonType.RELEASE_STOCK`.
- `revertStock()`: restores physical stock — `stockLevel += qty` (qty from `dto.stockLevel`).
  `ReasonType.REVERT_STOCK`.
- `increaseStockLevel()` / `decreaseStockLevel()`: warehouse adjustments (qty from `dto.stockLevel`),
  `ReasonType.INCREASED_BY_WAREHOUSE` / `CANCELLED_BY_WAREHOUSE`.

**How stock operations are triggered**:

- **Synchronously** via the REST controller: `reserveStocks` (`POST /reserve-stock`), `releaseStock`
  (`POST /revert-stock` — note the endpoint path says *revert* but calls `releaseStock`), `commitStock`
  (`POST /commit-stock`), `increaseStockLevel` (`POST /increase-stock`), `decreaseStockLevel`
  (`POST /decrease-stock`), plus the read/admin paths (`/prices`, `/available-count/{sku}`, `/{sku}`).
- **Asynchronously** via Kafka: `KafkaConsumerService` consumes `StockStatusMessage` events from
  `${topic.stock.status}` (produced by order-service's transactional outbox) and dispatches on
  `actionType` (**case-insensitive**): `"commit"` → `commitStock`, `"release"` → `releaseStock`,
  `"revert"` → `revertStock`. Every event is funneled through `InboxProcessor.processOnce` for structural
  exactly-once handling. There is **no** Kafka `"reserve"` event — reservation is synchronous REST only.
  See the [Kafka Integration & Inbox Pattern](#kafka-inbox) section.

**Key Entities**:

- `CoreEntity`: `@MappedSuperclass` holding the shared sequence-generated `id` and `@Version` fields
  plus Hibernate-proxy-safe `equals`/`hashCode`. `Inventory` and `InventoryChange` extend it; each
  subclass declares its own `@SequenceGenerator(name = "entity_seq", sequenceName = "<table>_seq",
  allocationSize = 50)` — see the [ID Generation](#id-generation) note.
- `Inventory`: Main inventory entity with stock levels, reserved stock, and product SKU
- `InventoryChange`: Audit trail for all inventory operations with reason types
- `InboxMessage`: Consumer-side idempotency row (`inbox_messages`), keyed by `messageId` — see the
  [Kafka Integration & Inbox Pattern](#kafka-inbox) section
- Uses optimistic locking with `@Version` for concurrent access control

**Service Layer**: `InventoryManager` handles all business logic including:

- Stock reservation/commit/revert operations with pessimistic locking
- Stock level adjustments from warehouse
- Validation and exception handling for insufficient stock
- Consistent audit logging pattern using centralized `saveChanges()` method

**Database Design**:

- PostgreSQL with Flyway migrations (12 migrations with schema evolution)
- Sequence-based ID generation for both main tables (`inventory_seq`, `inventory_change_seq`),
  pooled with `allocationSize = 50` — see the [ID Generation](#id-generation) note
- Unique constraints on product SKU for data integrity
- Comprehensive audit logging via inventory_change table
- Critical V9 migration: DECIMAL to BIGINT conversion for Java Long compatibility
- V10 migration: Added price columns (stock_price, sell_price) with DECIMAL(9,2) precision
- V11 migration: `inbox_messages` table for consumer-side Kafka dedup (partial cleanup index +
  topic/aggregate lookup index)
- V12 migration: `ALTER SEQUENCE inventory_seq / inventory_change_seq INCREMENT BY 50` to match the
  Hibernate `allocationSize = 50` pooled optimizer (the sequences were originally `INCREMENT BY 1`)

### API Endpoints (REST Controller)

Base path: `/v1/inventory/` (`@RequestMapping("/v1/inventory/")` on `InventoryController`)

- `GET /{sku}`: Get inventory details by SKU
- `POST /prices`: Look up `List<InventoryDto>` prices for a `List<String>` of SKUs (called by
  order-service during order creation; throws `EntityNotFoundException` if any SKU is missing)
- `GET /available-count/{sku}`: Get available stock count (`stockLevel − reservedStock`)
- `POST /reserve-stock`: Reserve stock for orders (`List<InventoryDto>` → `reserveStocks`)
- `POST /commit-stock`: Commit reserved stock — final sale (`commitStock`)
- `POST /revert-stock`: Free a reservation — maps to `releaseStock` (despite the `revert` path name)
- `POST /increase-stock`: Warehouse stock replenishment (`increaseStockLevel`)
- `POST /decrease-stock`: Warehouse stock reduction (`decreaseStockLevel`)
- `DELETE /{sku}`: Remove inventory item (bulk-deletes its audit rows first)

> There is **no** REST endpoint for `revertStock` — it is reachable only via the Kafka `"revert"`
> actionType.

## Security (OAuth2 Resource Server)

**Every endpoint requires `ADMIN` or `SERVICE`.** Unlike product-service — which publishes its
catalog reads — nothing here is public: stock levels, prices, and reservations are commercial data
with no anonymous use case.

| Path | Access |
|---|---|
| `/actuator/health/**`, `/actuator/prometheus` | public |
| `/swagger-ui/**`, `/v3/api-docs/**` | public (springdoc is disabled entirely in PROD) |
| everything else — all of `/v1/inventory/**` | `hasAnyRole("ADMIN", "SERVICE")` |

The two infrastructure carve-outs are not optional: the Compose healthcheck curls
`/actuator/health` and Prometheus scrapes `/actuator/prometheus` every 15 s. Locking those breaks
container startup and monitoring, not just observability.

**`SERVICE` carries the normal traffic here.** order-service drives `/prices` and `/reserve-stock`
during order creation under `client_credentials`; `ADMIN` exists for warehouse operations done by
hand (`/increase-stock`, `/decrease-stock`, `DELETE /{sku}`). Both roles are unrestricted across all
paths — split them if warehouse adjustments should stop being reachable by any service holding the
shared `webstore-service-client` secret.

Configured in `configs/SecurityConfig.java`. The issuer comes from
`spring.security.oauth2.resourceserver.jwt.issuer-uri` in the config repo's `inventory-service.yml`
(`${AUTH_ISSUER_URI:http://localhost:8076}`). Validation is **local** — the JWKS is discovered once,
lazily, and cached; auth-service is not in the request path.

**order-service authenticates as a machine client.** `PriceItemsStep` and `ReserveStockStep` reach
this service through a `RestClient` carrying a `client_credentials` token from
`webstore-service-client`, which auth-service grants `ROLE_SERVICE` — so those calls satisfy the
catch-all rule. See the outbound-tokens section of `order-service/CLAUDE.md`.

> **If those calls start failing, the symptom will not mention authentication.** A 401 here maps to
> `IllegalArgumentException` on the order side and surfaces as a 400. Check order-service's token
> first: is `AUTH_CLIENT_SECRET` (or the `auth_client_secret` Docker secret) set and equal to
> auth-service's, and does this service's `issuer-uri` match the token's `iss`?

**Kafka bypasses all of this.** Spring Security's filter chain only guards HTTP. The `commit` /
`release` / `revert` events order-service publishes reach `InventoryManager` without any
authentication, so the same stock mutations remain reachable over the broker. That's fine while the
broker is internal, but it means these rules protect one of two doors — don't read "inventory is
locked down" as more than it is.

**Uses the plain Spring `JwtAuthenticationConverter`, not product-service's custom one.**
product-service's `WebstoreJwtAuthenticationConverter` exists to lift `authUserId` into a typed
`CustomAuthentication`. Nothing here is user-scoped — inventory rows belong to SKUs, and the normal
caller is a machine token with no user at all — so duplicating those two classes would add a
consumer-less accessor. Add them if an endpoint ever needs to record *who* adjusted stock;
`inventory_change` is the natural place for that.

The claim-name + empty-prefix pair is identical to product-service's, and carries the identical
trap: the values arrive already prefixed (`ROLE_ADMIN`), so setting the prefix to `ROLE_` yields
`ROLE_ROLE_ADMIN` and 403s everything. See `product-service/CLAUDE.md` and `auth-service/CLAUDE.md`
for the full matched-pair explanation.

### Technology Stack

- Java 25 with Spring Boot 4.1.0 (Spring Framework 7)
- Spring Security via **`spring-boot-starter-oauth2-resource-server`** (not `-starter-security` —
  the resource-server starter pulls in `spring-security-config`/`-web` plus `-oauth2-jose`)
- Spring Cloud 2025.1.2 — Config for external configuration
- Spring Data JPA with PostgreSQL
- Web via `spring-boot-starter-webmvc` (renamed from `spring-boot-starter-web` in Spring Boot 4)
- Flyway for database migrations — via `spring-boot-starter-flyway` + `flyway-database-postgresql`
  (BOM-managed; `flyway-core` alone no longer auto-configures under Spring Boot 4)
- MapStruct for entity-DTO mapping
- Lombok for boilerplate code reduction
- Spring Kafka 4 — inbound consumer of `StockStatusMessage` (`JacksonJsonDeserializer`, Jackson 3);
  idempotent, **non-transactional** (the inbox provides exactly-once, not Kafka transactions)
- Spring `@Scheduled` — drives `InboxCleaner` (enabled by `@EnableScheduling` on the application class)
- Jackson **3** `ObjectMapper` (`tools.jackson.databind.ObjectMapper`) — serializes inbox payloads to
  JSON; `writeValueAsString` now throws the unchecked `tools.jackson.core.JacksonException`
  (`InboxProcessor.serialize` catches it instead of the old checked `JsonProcessingException`)

### Microservice Integration

- Connects to Spring Cloud Config Server (localhost:8071)
- Uses Spring Cloud dependencies for distributed system patterns
- Synchronously serves order-service's REST calls (`/prices`, `/reserve-stock`) — order-service reaches
  this service at a direct URL (`http://inventory-service:8074` in Docker, `http://localhost:8074` on host);
  there is no service discovery
- Consumes `StockStatusMessage` stock events from order-service over Kafka (`${topic.stock.status}`) —
  see the [Kafka Integration & Inbox Pattern](#kafka-inbox) section

## Concurrency and Data Consistency Patterns

### Locking Strategy

- **Optimistic Locking**: `@Version` annotation on entities for concurrent access detection
- **Pessimistic Locking**: Repository methods use `@Lock(LockModeType.PESSIMISTIC_WRITE)` for stock operations
- **Transactional Boundaries**: All service methods are `@Transactional` for ACID compliance

### Stock Level Management

- **Total Stock**: `stockLevel` field represents physical inventory
- **Reserved Stock**: `reservedStock` field tracks pending orders
- **Available Stock**: Calculated as `stockLevel - reservedStock` in repository queries
- **Reserve → Commit / Release lifecycle** prevents overselling (plus `revert` to restore physical stock)

<a id="kafka-inbox"></a>## Kafka Integration & Inbox Pattern

The inventory-service is a **Kafka consumer** for stock lifecycle events. order-service publishes
`commit` / `release` events (via its transactional outbox) to `${topic.stock.status}`; inventory
applies them and deduplicates redeliveries with a **transactional inbox** (package
`inventoryservice.inbox`). Together with order-service's outbox this gives the order ↔ inventory
flow end-to-end exactly-once semantics on top of at-least-once Kafka delivery.

`@EnableScheduling` and `@EnableConfigurationProperties(InboxProperties.class)` are declared on
`InventoryServiceApplication`.

### Kafka configuration (`configs/KafkaConfig`)

- **Consumer:** `ConsumerFactory<String, StockStatusMessage>` = `StringDeserializer` (key) +
  `JacksonJsonDeserializer<>(StockStatusMessage.class)` (value; Spring Kafka 4 / Jackson 3 — replaces the
  deprecated-for-removal `JsonDeserializer`). Group `{spring.application.name}-group`,
  `isolation.level=read_committed`, `enable.auto.commit=false`, `auto.offset.reset=earliest`.
- **Container factory** `kafkaListenerContainerFactory`: `RECORD` ack mode (offsets committed after
  the `@Transactional` listener returns), concurrency = `${num.partitions}`.
- A `NewTopic` bean auto-creates `${topic.stock.status}` with `${num.partitions}` / `${replication.factor}`.
- An **idempotent, non-transactional** producer (`stringKafkaTemplate`, `KafkaTemplate<String,String>`)
  is also configured for symmetry; there is no `transactional.id` and no `KafkaTransactionManager`.

> The producer (order-service) sends with `StringSerializer` (the pre-serialized JSON from its outbox
> row), so **no `__TypeId__` type header** is on the wire — the `JacksonJsonDeserializer` deserializes
> straight into `StockStatusMessage` from its constructor-configured target type.

### Consumed event contract

Value is a `StockStatusMessage` JSON object (package `inventoryservice.dto.kafka`):

- `stockLevels` — `Collection<StockLevelDto>`; each `StockLevelDto` carries `productSKU`, `stockLevel`,
  and `reservedStock`. `StockLevelDto.toInventoryDto()` copies all three into the `InventoryDto`.
  `commitStock` / `releaseStock` read the quantity from `dto.reservedStock`; `revertStock` reads it from
  `dto.stockLevel`.
- `actionType` (matched **case-insensitively**) — `"commit"` → `commitStock` (final sale),
  `"release"` → `releaseStock` (free reservation), `"revert"` → `revertStock` (restore physical stock).
  Any other value is logged as an error and the event **returns before being recorded**.
- `orderId` — String; the Kafka message **key** is also the orderId. Events with a `null` orderId are
  ignored (logged warn, return).

> order-service's producer puts the item quantity in `StockLevelDto.reservedStock`, which is exactly the
> field `commitStock` / `releaseStock` read — the two sides agree. (`revertStock` reads `stockLevel`, but
> order-service does not emit `"revert"` events today.)

### Consumer behavior (`managers/KafkaConsumerService.handleStockStatusUpdate`)

- Listener signature `(ConsumerRecord<String, StockStatusMessage> record, @Header(name = "X-Message-Id",
  required = false) String messageIdHeader)`, annotated `@Transactional`.
- Guards: null `orderId` → ignore; unknown `actionType` → log error and `return` (nothing recorded).
- Computes an idempotency key (`idempotencyKey`): prefers the `X-Message-Id` header (`StringUtils.hasText`),
  else the stable business key `stock-status:{orderId}:{actionType}`. **Never** `topic-partition-offset`.
- Builds an `InboxMessage` via `inboxProcessor.fromKafkaRecord(messageId, "Inventory", orderId,
  actionType, record, event)` and wraps the per-SKU `commitStock` / `releaseStock` / `revertStock`
  calls (selected by `actionType`) in `inboxProcessor.processOnce(msg, handler)`. A `false` return
  means a duplicate was skipped (logged).

### Inbox components (package `inventoryservice.inbox`)

| Class                    | Role                                                                                                                                                                                                       |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InboxMessage`           | JPA entity for `inbox_messages`. `messageId` (String) is the PK — the unique constraint *is* the dedup mechanism. Stores Kafka coordinates + JSON payload + `status`.                                      |
| `InboxStatus`            | Enum: `RECEIVED`, `PROCESSED`, `FAILED`                                                                                                                                                                    |
| `InboxMessageRepository` | `existsByMessageId` (fast-path check), `markProcessed`, `deleteProcessedBefore` (native batched cleanup)                                                                                                   |
| `InboxProcessor`         | `@Transactional(MANDATORY)` — runs inside the listener's tx. `processOnce(message, Runnable)` (record → run handler → mark PROCESSED) and lower-level `recordIfNew(message)`; plus `fromKafkaRecord(...)`. |
| `InboxCleaner`           | `@Scheduled(cron = ${inbox.cleanup-cron:0 30 3 * * *})`. Deletes PROCESSED messages older than `inbox.retention-days` (default 7) in batches of 1000 via `TransactionTemplate`.                            |
| `InboxProperties`        | `@ConfigurationProperties("inbox")` — `retentionDays` (7), `cleanupCron` (`0 30 3 * * *`).                                                                                                                 |

**Inbox row schema** (`V11__create_inbox_table.sql`): `message_id VARCHAR(255) PK`, `version`,
`aggregate_type`, `aggregate_id` (nullable), `event_type`, `topic_name`, `partition_no`,
`kafka_offset`, `payload TEXT`, `status` (`RECEIVED` default / `PROCESSED` / `FAILED`), `received_at`,
`processed_at`. Indexes: partial `idx_inbox_status_processed_at (status, processed_at) WHERE status =
'PROCESSED'` (cleanup) and `idx_inbox_topic_aggregate (topic_name, aggregate_id)` (debugging).

**Delivery semantics:** at-least-once Kafka; the inbox makes the **business effect** exactly-once.
The inbox-row insert, the `Inventory`/`InventoryChange` mutations, and the `PROCESSED` mark all commit
in the listener's single `@Transactional` boundary; the offset is committed after that (RECORD ack).

> **Known nuance:** `recordIfNew` catches `DataIntegrityViolationException` to handle a concurrent
> duplicate insert, but because the row's `@Id` is an assigned String the INSERT is deferred to flush,
> so the violation usually surfaces *after* the catch (and under `MANDATORY` propagation the tx is
> already rollback-only). In this topology messages for one order share a partition and are processed
> sequentially, so the race effectively can't fire; the catch is belt-and-suspenders, and the real
> dedup comes from `existsByMessageId` + rollback/redelivery. A DB upsert
> (`INSERT ... ON CONFLICT DO NOTHING`) would be the robust fix if the keying ever changes.

**Configuration (`inbox.*`):** not set in `webstore-config` today — `InboxProperties` defaults apply.
Override under `inbox:` in `C:\Data\Projects\webstore-config\config\inventory-service.yml` and commit/push.

<a id="id-generation"></a>## ID Generation

`Inventory` and `InventoryChange` inherit `id` from `CoreEntity`, which uses
`GenerationType.SEQUENCE` with generator name `entity_seq`. Each subclass binds that generator to its
own DB sequence with `allocationSize = 50`:

- `Inventory` → `inventory_seq`
- `InventoryChange` → `inventory_change_seq`

`allocationSize = 50` engages Hibernate's **pooled optimizer**: each `nextval` reserves a block of 50
ids in memory. The DB sequence increment **must equal** the allocationSize, otherwise consecutive
blocks overlap and collide on the primary key. The sequences were originally created `INCREMENT BY 1`
(V1/V2); **`V12__fix_sequence_increment.sql` raises both to `INCREMENT BY 50`** to match. If you change
`allocationSize`, add a migration to realign the sequence increment in lock-step.

## Key Development Patterns

### Entity-DTO Mapping

- **MapStruct** provides compile-time type-safe mapping between entities and DTOs
- **InventoryMapper** handles bidirectional conversion with null-value handling
- **Collection mapping** support for related entities

### Validation Strategy

- **Multi-layered validation**: Database constraints, Jakarta Bean Validation, and business logic validation
- **CustomValidator** service provides centralized validation with detailed error messages
- **Fail-fast approach**: Validation occurs at service method entry points

### Exception Handling

- **RestExceptionHandler** provides global exception handling with proper HTTP status mapping
- **NotEnoughStockException** for business logic violations (insufficient stock)
- **EntityNotFoundException** for missing inventory records

### Audit Trail Implementation

- **InventoryChange** entity captures every stock modification with:
    - Timestamp (automatic via `@CreationTimestamp`)
    - Change amount and reason type (enum-driven)
    - Associated inventory record reference
- **Centralized logging**: All audit records created through `saveChanges()` method

## Performance Considerations

### Delete Operations

- **Bulk delete pattern**: Uses `@Modifying` queries for efficient deletion of related records
- **Hybrid approach**: Bulk delete for audit records (`InventoryChange`), entity delete for main records (`Inventory`)
  to preserve audit trail
- **Example**: `inventoryChangeRepository.deleteByInventoryProductSKU(sku)` uses single SQL DELETE

### Database Query Optimization

- **Custom repository queries** with JPQL for complex operations
- **Pessimistic locking** on critical stock operations to prevent race conditions
- **Calculated fields** in queries (e.g., available stock calculation)