# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Gradle-based Spring Boot 3.5.5 project using Java 21.

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Run the application
./gradlew bootRun

# Skip tests during build
./gradlew build -x test
```

## Architecture Overview

### Service Role

The order-service is a microservice responsible for managing customer orders in a distributed e-commerce system. It
handles order creation from shopping carts, order status management, and coordinates with other services for inventory
management and payment processing.

### Layered Architecture

```
Controllers (REST API)
    ↓
Managers (Business Logic)
    ↓
Repositories (Data Access)
    ↓
PostgreSQL Database
```

### Key Architectural Patterns

**1. DTO Pattern with MapStruct**

- All entities have corresponding DTOs for API contracts
- MapStruct generates type-safe mappers at compile time
- Mappers are in `orderservice.mappers` package
- All mappers use `componentModel = "spring"` and `unmappedTargetPolicy = ReportingPolicy.IGNORE`

**2. Embedded Value Objects**

- `Address` is an `@Embeddable` component within `Order`
- Flattened in database as columns on orders table

**3. Optimistic Locking**

- Both `Order` and `OrderItem` entities use `@Version` field
- Prevents lost updates in concurrent modifications

**4. Transactional Processing**

- `OrderManager` methods use `@Transactional` for consistency
- Kafka consumer also uses `@Transactional` for exactly-once semantics

### Inter-Service Communication

**Synchronous (REST):**

- Calls cart-service to fetch cart items during order creation
- Uses Eureka-aware LoadBalanced `RestClient` from `RestConfig`
- Endpoint: `GET http://cart-service/v1/carts/cart/items/{cartID}`

**Asynchronous (Kafka):**

- **Produces:** Stock status events on `${topic.stock.status}` topic
- **Consumes:** Order status updates from `${topic.order.status}` topic
- Uses exactly-once semantics with transactional producer/consumer

### Kafka Integration Details

**Producer Configuration (KafkaConfig.java):**

- Transactional ID: `{application-name}-tx-{server-port}` (unique per instance)
- Idempotence enabled with `acks=all`
- Sends `StockStatusKafka` events with action types: "commit" or "release"
- Triggered on order status changes: CANCELLED/REFUNDED → release, DELIVERED → commit

**Consumer Configuration:**

- Consumer group: `{application-name}-group`
- Isolation level: `read_committed` (only reads committed transactions)
- Manual offset commits (handled by Spring Kafka with `@Transactional`)
- RECORD-level acknowledgment mode
- Processes `OrderStatusKafka` events mapping action types to `OrderStatus` enum

**Multi-Instance Deployment:**

- Each instance MUST run on a different port (e.g., 8080, 8081, 8082)
- This ensures unique transactional IDs per instance
- Without unique ports, instances will fence each other out

### Configuration Management

The service uses Spring Cloud Config for externalized configuration:

- Config Server URI: `http://localhost:8071`
- `application.yml` only contains bootstrap config
- All runtime properties (Kafka topics, database, etc.) come from Config Server

### Database Schema

**Tables:**

- `orders` - Main order table with embedded address fields
- `order_item` - Line items with unique constraint on (order_id, product_sku)

**Sequences:**

- `orders_seq` - For order IDs
- `order_item_seq` - For order item IDs

**Flyway Migrations:**

- Located in `src/main/resources/db/migration/`
- Initial schema: `V1__init_tables.sql`

### Order Lifecycle & Status Flow

**OrderStatus Enum:** PENDING → PROCESSING → SHIPPED → DELIVERED (or CANCELLED/REFUNDED)

**Status Change Triggers:**

1. Order created → PENDING
2. Payment confirmed (via Kafka) → PROCESSING
3. Shipped by fulfillment → SHIPPED
4. Completed delivery → DELIVERED (triggers stock "commit")
5. User cancels → CANCELLED (triggers stock "release")
6. Refund processed → REFUNDED (triggers stock "release")

### Business Logic Notes

**Order Creation (OrderManager.createOrder):**

1. Fetches cart items from cart-service via REST
2. Maps CartItemDto to OrderItemDto
3. Calculates total = sum(item.unitPrice * item.quantity)
4. Adds 20% tax: taxAmount = total * 0.20
5. Adds fixed shipping: shippingCost = 50.00
6. Sets initial status to PENDING
7. Persists order with cascaded order items

**Order Status Updates (OrderManager.changeOrderStatus):**

1. Validates order exists
2. Updates status
3. If CANCELLED, REFUNDED, or DELIVERED → triggers Kafka stock event
4. Persists changes

### API Endpoints

Base path: `/v1/orders`

| Method | Path                            | Request Body | Description         |
|--------|---------------------------------|--------------|---------------------|
| POST   | `/v1/orders`                    | OrderDto     | Create new order    |
| GET    | `/v1/orders/{orderID}`          | -            | Get order by ID     |
| GET    | `/v1/orders/user/{userID}`      | -            | Get all user orders |
| PUT    | `/v1/orders/{orderID}/{status}` | -            | Update order status |
| PUT    | `/v1/orders/{orderID}/cancel`   | -            | Cancel order        |

**Exception Handling:**

- `RestExceptionHandler` returns appropriate HTTP status codes
- 404 for `EntityNotFoundException`
- 400 for `IllegalArgumentException` and `NullPointerException`

### Important Implementation Details

**When adding new Kafka event types:**

1. Create DTO in `orderservice.dto.kafka` package
2. Add corresponding consumer factory and container factory in `KafkaConfig` if consuming
3. Use `@Transactional` on consumer methods for exactly-once semantics
4. Add null/validation checks in consumer handlers

**When modifying entities:**

1. Create Flyway migration script (next version number)
2. Update entity class
3. Update corresponding DTO
4. Update MapStruct mapper if needed
5. Consider optimistic locking (`@Version`) implications

**When adding new REST endpoints:**

1. Add method to `OrderController`
2. Implement business logic in `OrderManager`
3. Add exception handling to `RestExceptionHandler` if needed
4. Follow existing patterns for validation and transaction management

### Testing Notes

Current test coverage is minimal (only context load test exists). When adding tests:

- Unit tests should mock `OrderRepository` and external REST calls
- Integration tests should use `@SpringBootTest` with test containers for PostgreSQL and Kafka
- Use `@Transactional` on test methods for automatic rollback

### Dependencies to Be Aware Of

- **MapStruct 1.5.5.Final** - Compile-time code generation for mappers
- **Lombok** - Requires annotation processor configuration in IDE
- **Flyway 10.20.0** - Database migrations run automatically on startup
- **Spring Cloud 2025.0.0** - Includes Eureka client for service discovery
- **PostgreSQL** - Required runtime dependency
- **Kafka** - Required for event-driven communication

### Service Dependencies

This service requires the following to be running:

1. **Config Server** (localhost:8071) - For configuration
2. **Eureka Server** - For service discovery
3. **PostgreSQL** - For data persistence
4. **Kafka Broker** - For event streaming
5. **cart-service** - For fetching cart items during order creation
