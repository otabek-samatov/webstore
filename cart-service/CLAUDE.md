# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Spring Boot 3.5.5 application using Gradle with Java 21.

- **Build**: `./gradlew build`
- **Run**: `./gradlew bootRun`
- **Test**: `./gradlew test`
- **Clean**: `./gradlew clean`
- **Build JAR**: `./gradlew bootJar`
- **Build Docker Image**: `./gradlew bootBuildImage`

## Architecture Overview

This is a microservice implementing a shopping cart for an e-commerce webstore. It follows a clean layered architecture
pattern with proper separation of concerns.

### Core Components

- **Controllers** (`cartservice.controllers`): REST API endpoints with proper validation and error handling
- **Managers** (`cartservice.managers`): Business logic layer with transactional service implementations
- **Entities** (`cartservice.entities`): JPA entities with proper relationships and constraints
- **Repositories** (`cartservice.repositories`): Data access layer using Spring Data JPA with custom queries
- **DTOs** (`cartservice.dto`): Data transfer objects for API communication with validation annotations
- **Mappers** (`cartservice.mappers`): MapStruct mappers for entity-DTO conversion
- **Validators** (`cartservice.validators`): Custom input validation logic

### Key Entities

- **Cart**: Main shopping cart entity with lifecycle status (IN_PROGRESS, ABANDONED, COMPLETED)
    - Tracks user ownership via userId field
    - Uses optimistic locking with @Version
    - Manages bidirectional relationship with CartItems
- **CartItem**: Individual items within a cart with product SKU, quantity, and unit price
    - Has unique constraint on (cart_id, product_sku) to prevent duplicate products in same cart
    - Default quantity is 1L with @Positive validation
    - Includes optimistic locking with @Version
- **CartStatus**: Enum defining cart lifecycle states

### Database Schema

- **PostgreSQL** with Flyway migrations in `src/main/resources/db/migration/`
- **V1**: Initial tables with sequences for primary key generation
- **V2**: Adds unique constraint to prevent duplicate products per cart
- **Sequences**: `cart_seq` and `cart_item_seq` for ID generation

### Transaction Management

All write operations are properly managed with `@Transactional`:

- Cart creation and modification
- Cart item addition, removal, and quantity updates
- Cart checkout and completion
- Bulk operations use `@Modifying` queries with transaction support

### API Design

**Base Path**: `/v1/carts/cart`

**Key Endpoints**:

- `GET /{userID}` - Get user's active cart
- `DELETE /{userID}` - Delete user's cart
- `GET /{userID}/total` - Get cart total (handles null values)
- `POST /{userID}/items` - Add multiple items (bulk operation)
- `POST /{userID}/item` - Add single item
- `PUT /item` - Update item quantity
- `DELETE /item/{itemID}` - Remove specific item
- `GET /item/{itemID}` - Get specific item
- `GET /{userID}/items` - Get all items in user's cart
- `POST /{userID}/complete` - Checkout cart

**Validation**: All request bodies use `@Valid` for automatic validation

### Integration Points

**Spring Cloud Stack**:

- **Config Server**: `http://localhost:8071` for externalized configuration
- **Eureka Client**: Service discovery integration
- **Actuator**: Health checks and monitoring endpoints

**External Service Placeholders** (for future implementation):

- Product pricing service (`getUnitPrice`)
- Inventory service (`checkQuantity`, `reserveStock`, `releaseStock`)
- Order service (`createOrder`)

### Key Technologies

- **Spring Boot 3.5.5** with Spring Cloud 2025.0.0
- **JPA/Hibernate** with PostgreSQL driver
- **Lombok** for boilerplate reduction
- **MapStruct 1.5.5** for type-safe mapping
- **Bean Validation** for input validation
- **Flyway 10.20.0** for database migrations
- **DataFaker 2.4.2** for test data generation

### Known Security Considerations

**Current State**: The service currently lacks user authorization on item-level operations. The following endpoints
should verify item ownership:

- `PUT /item` - Should verify the item belongs to the requesting user
- `DELETE /item/{itemID}` - Should verify the item belongs to the requesting user
- `GET /item/{itemID}` - Should verify the item belongs to the requesting user

This is acceptable for MVP but should be addressed before production deployment.

### Development Notes

- The service builds and runs successfully with all tests passing
- Core business logic methods are implemented as placeholders for external service integration
- Data integrity is enforced through database constraints and entity validation
- Transaction boundaries are properly defined for data consistency
- Error handling provides meaningful messages for different failure scenarios