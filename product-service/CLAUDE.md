# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot microservice for managing products in a webstore. It's part of a larger Spring Cloud ecosystem
using Spring Cloud Config for centralized configuration. There is **no service discovery** — services
reach each other directly by DNS name + fixed port (Docker Compose DNS in containers, `localhost` on host runs).

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
- Uses Java 25, Spring Boot 4.1.0 (Spring Framework 7), Spring Cloud 2025.1.2

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

## Security (OAuth2 Resource Server)

product-service is the **first webstore service to validate tokens**. The catalog is readable by
anyone; modifying it requires a JWT issued by auth-service whose holder has the `ADMIN` role.

| Path | Access |
|---|---|
| `GET /v1/books/**` | public — all four controllers' reads |
| `/actuator/health/**`, `/actuator/prometheus` | public |
| `/swagger-ui/**`, `/v3/api-docs/**` | public (springdoc is disabled entirely in PROD) |
| everything else — POST / PUT / DELETE | `hasAnyRole("ADMIN", "SERVICE")` |

> **Roles are the only authorization concept platform-wide.** auth-service has no permissions —
> `READ` / `WRITE` were removed there in `V2`, and a token carries exactly one role. Any rule added
> here must therefore be a `hasRole(...)` over `ADMIN` / `CUSTOMER` / `SERVICE`; there is no finer
> grain to reach for. See `auth-service/CLAUDE.md`.

**`SERVICE` is unrestricted here — deliberately.** It is the role on `client_credentials` tokens, so
another webstore service calling in may do anything a catalog admin can. That is a real trust
decision, not an oversight: the role is granted by client registration, and every service shares the
one `webstore-service-client`, so any service holding the secret can write to the catalog. Narrow it
to specific paths if that becomes too broad.

Configured in `configs/SecurityConfig.java`. The issuer comes from
`spring.security.oauth2.resourceserver.jwt.issuer-uri` in the config repo's `product-service.yml`
(`${AUTH_ISSUER_URI:http://localhost:8076}`).

**Validation is local.** The JWKS is fetched from the issuer once and cached — auth-service is not
called per request and is not in the request path.

**Rules are default-deny.** Reads are listed explicitly and everything else falls through to
`hasAnyRole("ADMIN", "SERVICE")`. A controller added later is protected until someone deliberately
opens it, rather than public by accident. Keep it that way — don't invert to "permit everything,
protect the writes by name".

### Four things that will bite

**CSRF must stay disabled.** This is a stateless bearer-token API — nothing is attached
automatically by the browser, so CSRF has no attack surface. Leave it enabled and every
POST/PUT/DELETE returns **403 despite a perfectly valid token**, with nothing in the response
pointing at CSRF.

**The `JwtAuthenticationConverter` is not optional.** The default converter reads the `scope` claim
and prefixes each value with `SCOPE_`, so a user token yields `SCOPE_openid` / `SCOPE_profile` and
never a role — every write would 403. The bean overrides the claim name to `authorities` and clears
the prefix so values arrive verbatim (`ROLE_ADMIN`, `ROLE_CUSTOMER`). It only works because
auth-service's `OAuth2TokenCustomizer` puts that claim on the token in the first place; the two are
a matched pair, and changing the claim name on one side breaks the other silently.

**The empty authority prefix looks wrong and must stay.** Now that the claim holds only roles it is
tempting to set the prefix to `ROLE_` — but auth-service already applies it before the value goes on
the wire, so that yields `ROLE_ROLE_ADMIN` and 403s every admin write. Either both sides carry the
prefix (as today) or neither does.

**`issuer-uri` must equal the token's `iss` claim exactly**, or every request 401s. auth-service
currently derives its issuer from the request host, so the `localhost:8076` default only lines up
for host runs. See `auth-service/CLAUDE.md` — the issuer needs pinning before this works in Docker
or through the gateway.

**`client_credentials` tokens can write here.** They carry no user, but auth-service reads the role
from the client's registration and puts `ROLE_SERVICE` on the token — which the catch-all rule
accepts. A machine token that 403s means the client has no `settings.client.role` on its
registration, not that machine tokens are inherently roleless.

### Diagnosing a rejected write

- **401** — token missing, expired, malformed, or `iss` mismatch. Check `issuer-uri` first.
- **403** — token is valid but its holder is neither `ADMIN` nor `SERVICE`. Decode it: no
  `authorities` claim at all means the problem is on the auth-service side (for a machine token,
  most likely a missing client role setting); `ROLE_CUSTOMER` means the account genuinely lacks the
  role; `ROLE_ADMIN` or `ROLE_SERVICE` present *and still* 403 means the converter isn't wired, or
  has picked up a `ROLE_` prefix it shouldn't have.

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
- **Application config**: `application.yml` with config server import (`spring.application.name: product-service`
  keys the config lookup and the gateway route target)

### Spring Boot 4 notes

- Security comes from **`spring-boot-starter-oauth2-resource-server`** (not `-starter-security` —
  the resource-server starter pulls in `spring-security-config`/`-web` plus `-oauth2-jose`).
- Uses the **`spring-boot-starter-webmvc`** starter (renamed from `spring-boot-starter-web` in Spring Boot 4).
- Flyway is wired via **`spring-boot-starter-flyway`** + `flyway-database-postgresql` (BOM-managed
  versions, ~Flyway 11) — `flyway-core` alone no longer auto-configures migrations in Spring Boot 4.
- This service does not use Kafka/Jackson directly, so the Jackson 2 → 3 migration doesn't touch it.