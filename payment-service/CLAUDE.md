# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The Payment Service is part of the Webstore e-commerce microservices application. It handles payment processing and
refund operations for customer orders.

**Tech Stack:**

- Java 21
- Spring Boot 3.5
- Spring Data JPA (Hibernate)
- PostgreSQL 17
- Spring Cloud (Config, Eureka)
- MapStruct for DTO mapping
- Flyway for database migrations
- Lombok for boilerplate reduction

**Port:** Not configured locally (managed by Config Server at `http://localhost:8071`)

## Build Commands

```bash
# Build without tests
./gradlew build -x test

# Build with tests
./gradlew build

# Clean build
./gradlew clean build

# Run application locally (requires PostgreSQL, Config Server, and Eureka running)
./gradlew bootRun

# Run specific test
./gradlew test --tests "paymentservice.PaymentServiceApplicationTests"

# Generate sources (MapStruct implementations)
./gradlew compileJava
```

## Architecture

### Microservice Context

Payment Service is one of 9 microservices in the Webstore application:

- **Config Service** (port 8071): Centralized configuration
- **Discovery Service**: Netflix Eureka service registry
- **Gateway Service**: API gateway (Spring Cloud Gateway)
- **Order Service**: Creates orders that trigger payments
- **Payment Service**: This service - processes payments and refunds
- Others: Product, Inventory, User, Cart, Auth services

Payment Service integrates with:

- **Order Service**: Receives payment requests for orders, should update order status after payment
- **Config Server**: Retrieves runtime configuration
- **Eureka**: Registers itself for service discovery

### Service Layer Architecture

The service follows a standard layered architecture:

1. **Controller Layer** (`controllers/`)
    - `PaymentController`: REST endpoints for payment and refund operations
    - `RestExceptionHandler`: Global exception handling with `@RestControllerAdvice`

2. **Service/Manager Layer** (`managers/`)
    - `PaymentManager`: Core business logic, transaction boundaries, validation
    - `PaymentProcess`: Interface for payment gateway abstraction
    - `PaymentMockProxy`: Mock implementation (simulates external payment gateway)

3. **Repository Layer** (`repositories/`)
    - `PaymentRepository`: JPA repository with custom query `findPaymentByOrderId()`
    - `RefundRepository`: JPA repository with JPQL query for `findRefundByPaymentId()`

4. **Data Layer**
    - **Entities** (`entities/`): JPA entities with Hibernate proxy-safe equals/hashCode
    - **DTOs** (`dto/`): Request/response objects with validation annotations
    - **Mappers** (`mappers/`): MapStruct interfaces (implementations auto-generated)

### Key Design Patterns

- **Strategy Pattern**: `PaymentProcess` interface allows swapping payment gateway implementations
- **DTO Pattern**: Separate DTOs from entities to control API surface and add validation
- **Repository Pattern**: Spring Data JPA repositories abstract database access
- **Optimistic Locking**: `@Version` fields in entities prevent concurrent modification issues

### Transaction Boundaries

All payment and refund processing methods in `PaymentManager` are marked `@Transactional`:

- `processPayment()`: Validates, processes payment, saves payment, attempts order status update
- `processRefund()`: Validates, processes refund, updates payment status, saves both entities

**Critical Issue**: `updateOrderStatus()` currently throws `UnsupportedOperationException`, causing all transactions to
rollback. This needs implementation to communicate with Order Service.

**Hibernate DDL Configuration**: The service uses `spring.jpa.hibernate.ddl-auto = validate`, which means Hibernate only
validates the schema matches entities but does not create or modify database objects. All schema changes must be managed
through Flyway migrations.

### Database Schema

Tables managed by Flyway migrations (`src/main/resources/db/migration/`):

- **payment**: Stores payment records
    - Primary key: `id` (BIGINT, sequence-generated)
    - Indexed on `order_id` (unique) and `user_id` for fast lookups
    - Enum field `payment_status`: COMPLETED, FAILED, REFUNDED
    - `amount` field: DECIMAL(9,2), NOT NULL
    - `created_at` timestamp: Automatically set by Hibernate `@CreationTimestamp`
    - Uses optimistic locking with `version` column

- **refund**: Stores refund records
    - Primary key: `id` (BIGINT, sequence-generated)
    - Foreign key to `payment` table via `payment_id` (indexed)
    - Enum field `refund_status`: COMPLETED, FAILED
    - `refund_amount` field: DECIMAL(9,2), NOT NULL
    - `created_at` timestamp: Automatically set by Hibernate `@CreationTimestamp`
    - Uses optimistic locking with `version` column
    - Relationship: `@ManyToOne` from Refund to Payment, `@OneToMany` from Payment to Refund

**Migrations**:

- `V1__init_tables.sql`: Creates initial payment and refund tables with sequences and indexes
- `V2__add_unique_constraint.sql`: Adds unique constraint on `payment.order_id` to prevent duplicate payments
- `V3__add_index.sql`: Adds index on `refund.payment_id` for query performance
- `V4__add_columns.sql`: Adds `created_at` timestamps with defaults and sets `amount` to NOT NULL

### Configuration Management

The service uses Spring Cloud Config with a two-tier configuration approach:

1. **Local bootstrap** (`application.yml`):
    - Application name
    - Config server URI
    - Uses `optional:configserver:` for fallback

2. **Remote configuration**: Retrieved from Config Server at runtime
    - Database connection details
    - Eureka registration
    - Actuator endpoints
    - Server port assignment

To run locally, ensure Config Server is running at `http://localhost:8071` with payment-service configuration.

### Payment Processing Flow

1. Client POST to `/v1/payments` with `PaymentDto`
2. `PaymentController` validates DTO (`@Valid`)
3. `PaymentManager.processPayment()`:
    - Validates business rules (non-null IDs, non-null non-negative amount)
    - Checks for duplicate payment using `getCountByOrderAndStatus()` (prevents double-payment)
    - Maps DTO to entity using `paymentMapper.toEntity()`
    - Calls `PaymentProcess.processPayment()` (mock or real gateway)
    - Sets status based on result (COMPLETED or FAILED)
    - Saves to database
    - Attempts `updateOrderStatus()` (currently throws exception)
4. Returns `PaymentDto` with payment ID and status

**Payment Mock Proxy**: `PaymentMockProxy` simulates payment gateway with 99% success rate for testing.

### Refund Processing Flow

1. Client POST to `/v1/payments/refund` with `RefundDto`
2. `PaymentController` validates DTO (`@Valid`)
3. `PaymentManager.processRefund()`:
    - Validates refund amount is positive and non-null
    - Fetches original payment by ID
    - Validates payment status is COMPLETED (cannot refund failed payments)
    - Validates refund amount equals payment amount (full refunds only)
    - Checks for existing refund using `getCountByOrderAndStatus()` (prevents double-refund)
    - Maps DTO to entity using `refundMapper.toEntity()`
    - Calls `PaymentProcess.processRefund()` (always returns true in mock)
    - On success: sets refund status to COMPLETED, payment status to REFUNDED
    - On failure: sets refund status to FAILED
    - Adds refund to payment using bidirectional helper method `payment.addRefund()`
    - Saves payment (cascade saves refund)
4. Returns `RefundDto` with refund ID and status

**Business Rules**:

- Only COMPLETED payments can be refunded
- Only full refunds are allowed (refund amount must equal payment amount)
- Only one refund per payment is allowed
- Payment status changes to REFUNDED after successful refund

### MapStruct Code Generation

MapStruct generates mapper implementations at compile time:

- Source: `src/main/java/paymentservice/mappers/*.java`
- Generated: `build/generated/sources/annotationProcessor/java/main/paymentservice/mappers/*Impl.java`

Mappers are automatically injected as Spring beans via `componentModel = MappingConstants.ComponentModel.SPRING`.

## API Endpoints

### Payment Endpoints

- `POST /v1/payments` - Create/process a payment
- `GET /v1/payments/{paymentID}` - Get payment by ID
- `GET /v1/payments/order/{orderID}` - Get payment by order ID

### Refund Endpoints

- `POST /v1/payments/refund` - Create/process a refund
- `GET /v1/payments/refund/{refundID}` - Get refund by ID
- `GET /v1/payments/refund/payment/{paymentID}` - Get all refunds for a payment (returns List)

## Entity Relationships and Validation

### Payment Entity

- **Required fields**: `orderId` (unique), `userId`, `amount` (NOT NULL, @PositiveOrZero), `paymentStatus`
- **Auto-generated**: `id`, `createdAt`, `version`
- **Relationship**: One-to-Many with Refund (bidirectional)
- **Helper methods**: `addRefund()`, `removeRefund()`, `getRefunds()` (returns defensive copy)
- **Collection protection**: `@Setter(lombok.AccessLevel.NONE)` on refunds collection

### Refund Entity

- **Required fields**: `refundAmount` (@Positive), `refundStatus`, `payment` (ManyToOne, NOT NULL)
- **Auto-generated**: `id`, `createdAt`, `version`
- **Cascade**: `CascadeType.PERSIST` from Refund to Payment

### DTOs and Validation

- All required fields marked with `@NotNull`
- Amount fields validated with `@PositiveOrZero` (Payment) or `@Positive` (Refund)
- Validation happens at controller level with `@Valid` annotation
- Additional business logic validation in `PaymentManager`

## Known Issues and Technical Debt

1. **Critical**: `PaymentManager.updateOrderStatus()` throws `UnsupportedOperationException` - needs inter-service
   communication implementation to notify Order Service of payment/refund status changes

## Development Workflow

When adding new features:

1. **Database changes**: Add Flyway migration in `src/main/resources/db/migration/` with format
   `V{N}__{description}.sql`
2. **Entity changes**: Update JPA entity, ensure `@Version` field exists for optimistic locking
3. **DTO changes**: Update DTO with validation annotations, MapStruct will auto-generate mapper changes
4. **Business logic**: Add to `PaymentManager`, use `@Transactional` for write operations
5. **API changes**: Add controller endpoints, ensure `@Valid` on request bodies
6. **Rebuild**: Run `./gradlew compileJava` to regenerate MapStruct implementations

## Code Quality Highlights

The codebase follows Spring Boot best practices:

1. **Proper Layering**: Clear separation between controllers, services, repositories, entities, and DTOs
2. **Validation Strategy**: Multi-layered validation (annotation-based at DTO level, programmatic in service layer)
3. **Transaction Management**: `@Transactional` boundaries clearly defined in service layer
4. **Entity Design**:
    - Hibernate proxy-safe `equals()` and `hashCode()` implementations
    - Optimistic locking with `@Version` fields
    - Bidirectional relationship management with helper methods
5. **Mapper Pattern**: Clean DTO-Entity conversion using MapStruct
6. **Database Schema Management**: Flyway migrations with proper sequencing
7. **Idempotency Protection**: Prevents duplicate payments and refunds via database queries
8. **Index Strategy**: Strategic indexing on frequently-queried columns

## Integration Points

### Order Service Integration (TODO)

When implementing `updateOrderStatus()`:

- Remove the `throw new UnsupportedOperationException()`
- Implement REST call to Order Service (use `RestTemplate`, `WebClient`, or Spring Cloud OpenFeign)
- Order Service should expose endpoint to update order status based on payment status
- Handle failures gracefully:
    - Consider compensating transaction pattern for payment failures
    - May need saga pattern for distributed transaction management
    - Log failures but don't rollback payment transaction (payment already processed)
    - Consider async notification with retry mechanism
