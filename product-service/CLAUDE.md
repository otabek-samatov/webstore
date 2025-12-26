# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot microservice for managing products in a webstore. It's part of a larger Spring Cloud ecosystem
using Eureka for service discovery and Spring Cloud Config for centralized configuration.

## Development Commands

### Build and Run

- `./gradlew build` - Build the application
- `./gradlew bootRun` - Run the application
- `./gradlew test` - Run all tests
- `./gradlew test --tests ProductServiceApplicationTests` - Run a single test class
- `./gradlew test --tests "*BookController*"` - Run tests matching pattern

### Development Workflow

- Application auto-generates sample data on startup via `MainGenerator`
- Database migrations handled automatically by Flyway on startup
- Uses Java 21, Spring Boot 3.4.4, Spring Cloud 2024.0.1

## Core Architecture

**Entity-Service-Controller Pattern** with sophisticated domain modeling:

### Domain Model Relationships

```
Book (central entity)
├── Publisher (many-to-one, required)
├── Authors (many-to-many via Book_Author table)
├── Categories (many-to-many via Book_Category table)
└── Book Images (element collection via Book_Images table)

Category (hierarchical)
└── Parent Category (self-referencing, optional)
```

### Layer Responsibilities

- **Controllers** (`/v1/books/{resource}`): REST endpoints with consistent CRUD patterns
- **Services** (`*Manager`): Business logic with `createOrUpdate` patterns, cascade delete protection
- **Repositories**: Spring Data JPA with custom `@Query` methods for complex lookups
- **DTOs**: Immutable with `@Value` + `@Builder`, separate validation from entities
- **Mappers**: MapStruct with partial updates and relationship ID mapping

## Business Logic Patterns

### Service Layer Design

- **Unified CRUD**: All managers use `createOrUpdate(dto, isCreateFlag)` pattern
- **Reference Management**: Bulk ID validation with `getReferenceByIDs()` methods
- **Cascade Delete Protection**: Cannot delete Publisher/Author/Category if books exist
- **Transaction Boundaries**: `@Transactional` on create/update operations

### Data Generation Strategy

- `MainGenerator` creates 10K books, 1K authors, 100 publishers on startup
- 3-level category hierarchy (Fiction/Non-Fiction → subcategories → specializations)
- Uses Datafaker for realistic test data with duplicate prevention via ISBN/name checks

## Key Implementation Details

### Entity Design Patterns

- **Defensive Collections**: Custom add/set/remove methods with `Set.copyOf()` returns
- **Hibernate Proxy Safety**: Proper equals/hashCode handling proxy objects
- **Optimistic Locking**: All entities use `@Version` fields
- **Validation Strategy**: Bean validation on entities + DTOs, centralized via `CustomValidator`

### Database Integration

- **PostgreSQL** with sequence generators (allocationSize = 1)
- **Flyway migrations** in `src/main/resources/db/migration/`
- **Performance queries**: Uses `join fetch` for eager loading, `countByIdIn()` for bulk validation
- **JdbcClient integration**: Raw SQL for random selection in data generation

### Configuration

- **Spring Cloud Config**: External config server at `http://localhost:8071`
- **Service Discovery**: Eureka client registration as `product-service`
- **Application config**: `application.yml` with config server import