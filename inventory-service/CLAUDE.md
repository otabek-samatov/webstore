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
- The service runs on port 8080 (default Spring Boot port)
- Health check available at: `http://localhost:8080/actuator/health`

### Database Operations

- Database migrations are handled by Flyway automatically on startup
- Migration files located in `src/main/resources/db/migration/`
- PostgreSQL database required (configured in application.yml)

## Architecture Overview

This is a Spring Boot microservice for inventory management within a webstore architecture.

### Core Components

**Inventory Management System**: The service implements a two-phase commit pattern for stock operations:

- `reserveStock()`: Reserves stock for pending orders
- `commitStock()`: Finalizes the reservation (decreases actual stock)
- `revertStock()`: Releases reserved stock back to available pool

**Key Entities**:

- `Inventory`: Main inventory entity with stock levels, reserved stock, and product SKU
- `InventoryChange`: Audit trail for all inventory operations with reason types
- Uses optimistic locking with `@Version` for concurrent access control

**Service Layer**: `InventoryManager` handles all business logic including:

- Stock reservation/commit/revert operations with pessimistic locking
- Stock level adjustments from warehouse
- Validation and exception handling for insufficient stock
- Consistent audit logging pattern using centralized `saveChanges()` method

**Database Design**:

- PostgreSQL with Flyway migrations (10 migrations with schema evolution)
- Sequence-based ID generation for both main tables
- Unique constraints on product SKU for data integrity
- Comprehensive audit logging via inventory_change table
- Critical V9 migration: DECIMAL to BIGINT conversion for Java Long compatibility
- V10 migration: Added price columns (stock_price, sell_price) with DECIMAL(9,2) precision

### API Endpoints (REST Controller)

Base path: `/v1/inventories/inventory`

- `GET /{sku}`: Get inventory details by SKU
- `GET /available-count/{sku}`: Get available stock count (calculates stockLevel - reservedStock)
- `POST /reserve-stock`: Reserve stock for orders
- `POST /commit-stock`: Commit reserved stock (final sale)
- `POST /revert-stock`: Release reserved stock (cancelled order)
- `POST /increase-stock`: Warehouse stock replenishment
- `POST /decrease-stock`: Warehouse stock reduction
- `DELETE /{sku}`: Remove inventory item

### Technology Stack

- Java 21 with Spring Boot 3.4.4
- Spring Cloud Config for external configuration
- Spring Data JPA with PostgreSQL
- Flyway for database migrations
- MapStruct for entity-DTO mapping
- Lombok for boilerplate code reduction
- Eureka client for service discovery

### Microservice Integration

- Connects to Spring Cloud Config Server (localhost:8071)
- Registers with Eureka service registry
- Uses Spring Cloud dependencies for distributed system patterns

## Concurrency and Data Consistency Patterns

### Locking Strategy

- **Optimistic Locking**: `@Version` annotation on entities for concurrent access detection
- **Pessimistic Locking**: Repository methods use `@Lock(LockModeType.PESSIMISTIC_WRITE)` for stock operations
- **Transactional Boundaries**: All service methods are `@Transactional` for ACID compliance

### Stock Level Management

- **Total Stock**: `stockLevel` field represents physical inventory
- **Reserved Stock**: `reservedStock` field tracks pending orders
- **Available Stock**: Calculated as `stockLevel - reservedStock` in repository queries
- **Two-phase commit**: Reserve → Commit/Revert pattern prevents overselling

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