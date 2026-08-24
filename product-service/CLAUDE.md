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

product-service was the first webstore service to validate tokens; all five business services do now.
It is the **only one with a public surface** — the catalog is readable by anyone, while modifying it
requires a JWT issued by auth-service whose holder has the `ADMIN` or `SERVICE` role. The others
(inventory, payment, user) require a role on every endpoint; order-service requires only
`authenticated()`.

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

Split across two places: `configs/SecurityConfig.java` holds the filter chain and the rules;
`security/` holds the token→`Authentication` conversion (see below). The issuer comes from
`spring.security.oauth2.resourceserver.jwt.issuer-uri` in the config repo's `product-service.yml`
(`${AUTH_ISSUER_URI:http://localhost:8076}`).

**The `JwtDecoder` is auto-configured — that's why `SecurityConfig` never names a key URL.** Setting
`issuer-uri` makes Spring Boot publish a `JwtDecoder` bean, which `.jwt(...)` picks up from the
context; the config class only attaches the converter. Calling `jwkSetUri(...)` or `decoder(...)` in
the DSL would *override* that bean.

**`issuer-uri` is deliberately not `jwk-set-uri`.** Both properties produce a working decoder, and
the JWKS one looks simpler — it skips a round trip by naming
`http://localhost:8076/oauth2/jwks` directly. Take that shortcut and you lose the only thing that
ties a token to *this* authorization server:

| | `jwk-set-uri` | `issuer-uri` (used here) |
|---|---|---|
| How the JWKS is found | hardcoded URL | discovery: `GET {issuer}/.well-known/openid-configuration` → `jwks_uri` |
| `iss` claim validated | **no** | **yes** — the discovered `issuer` becomes a `JwtIssuerValidator` |
| `exp` / `nbf` validated | yes | yes |

With `jwk-set-uri`, *any* token signed by a key in that key set is accepted, whoever minted it and
whatever it claims to be. The discovery hop also cross-checks the document's `issuer` against the
configured value at decoder-build time.

**Validation is local.** Discovery runs **once, lazily on the first authenticated request**
(Boot wraps it in a `SupplierJwtDecoder`, so the app still starts if auth-service is down), and the
JWKS is cached after that — auth-service is not called per request and is not in the request path.

### The `Authentication` in the security context

`security/WebstoreJwtAuthenticationConverter` turns the validated `Jwt` into a
`security/CustomAuthentication` — a `JwtAuthenticationToken` subclass carrying the `authUserId`
claim. Two classes, two jobs:

| Class | Job |
|---|---|
| `WebstoreJwtAuthenticationConverter` | `Converter<Jwt, CustomAuthentication>`; owns the claim names and the authorities mapping |
| `CustomAuthentication` | `extends JwtAuthenticationToken`; adds `getAuthUserId()` |

**It is deliberately not named `JwtAuthenticationConverter`** — that is the Spring class it replaces,
and two types with one name in the same config is how an import gets silently swapped.

**The authorities mapping is delegated to `JwtGrantedAuthoritiesConverter`, not reimplemented.**
Reading the claim by hand (`jwt.getClaimAsStringList("authorities")`) costs four lines and loses
three behaviours: an absent claim becomes an NPE → **500** instead of an empty collection → **403**;
a claim arriving as a delimited string instead of an array is mishandled; and the claim name, prefix
and delimiter stop living in one recognisable place. The empty-claim case is one this system
*deliberately produces* — a machine client with no `settings.client.role` is meant to fail closed —
so that first row is a live path, not a hypothetical.

**Reading the id.** `@AuthenticationPrincipal` does **not** give you `CustomAuthentication` — it
resolves `getPrincipal()`, which on `JwtAuthenticationToken` and therefore on our subclass is the
`Jwt`. The constructor passes the token in as token, principal *and* credentials, and subclassing
doesn't change that. Take the `Authentication` instead:

```java
@PostMapping
public ResponseEntity<BookDto> create(@RequestBody BookDto dto, Authentication authentication) {
    Long authUserId = authentication instanceof CustomAuthentication custom
            ? custom.getAuthUserId()
            : null;
}
```

**Use `instanceof`, not a cast.** It is null-safe (`null instanceof X` is `false`), so one
expression covers authenticated, anonymous, and null without a separate guard. Declaring the
parameter as `CustomAuthentication` directly also works — Spring MVC's principal resolver accepts
any type assignable from the current `Authentication` — and is fine on the write endpoints, where
`hasAnyRole("ADMIN","SERVICE")` guarantees a JWT.

Three cases, all live, and **the first behaves differently depending on how you reach the
`Authentication`**:

| | Controller parameter | `SecurityContextHolder` |
|---|---|---|
| Anonymous request (`GET /v1/books/**` is `permitAll`) | **`null`** — `SecurityContextHolderAwareRequestWrapper.getUserPrincipal()` returns null for anonymous | the `AnonymousAuthenticationToken`, principal `"anonymousUser"` |

A blind `((CustomAuthentication) authentication)` fails differently in each: on the parameter it
casts `null` successfully and then NPEs on the method call; via `SecurityContextHolder` it throws
`ClassCastException`. `instanceof` handles both.

- **Machine tokens.** `client_credentials` names no user, so `getAuthUserId()` returns `null` by
  design. This service accepts those on every write, and unboxing to `long` NPEs on exactly that
  traffic — the traffic manual testing doesn't cover.
- **Off-request threads.** `SecurityContextHolder` is a `ThreadLocal`; it is empty in `@Scheduled`,
  `@Async`, and any thread the app spawns.

Note that `null` from the snippet above collapses two distinct situations — "nobody authenticated"
and "a service authenticated, which names no user". Nothing needs to tell them apart today, since
writes treat `ADMIN` and `SERVICE` identically; split the `instanceof` into an if/else-if if that
changes.

**The claim is minted and read as a string, then parsed.** A JSON integer deserializes as `Integer`
or `Long` depending on magnitude and `Jwt.getClaim` casts unchecked — a numeric claim would work
until ids grew past `Integer.MAX_VALUE`, then throw `ClassCastException`. `getClaimAsString`
converts rather than casts. A non-numeric value throws instead of degrading to `null`: it would mean
the issuer changed the format, and that should fail loudly.

> ⚠️ **`authUserId` is auth-service's id, not user-service's.** The two services own separate `users`
> tables whose ids are different numbers for the same person, and nothing reconciles them. The claim
> is named after its store precisely so this can't be forgotten — do not use it to look anything up
> in `user_schema` until that is settled. See the open architectural questions in
> `auth-service/CLAUDE.md`.

**Rules are default-deny.** Reads are listed explicitly and everything else falls through to
`hasAnyRole("ADMIN", "SERVICE")`. A controller added later is protected until someone deliberately
opens it, rather than public by accident. Keep it that way — don't invert to "permit everything,
protect the writes by name".

### Five things that will bite

**CSRF must stay disabled.** This is a stateless bearer-token API — nothing is attached
automatically by the browser, so CSRF has no attack surface. Leave it enabled and every
POST/PUT/DELETE returns **403 despite a perfectly valid token**, with nothing in the response
pointing at CSRF.

**The converter is not optional.** The default converter reads the `scope` claim and prefixes each
value with `SCOPE_`, so a user token yields `SCOPE_openid` / `SCOPE_profile` and never a role —
every write would 403. `WebstoreJwtAuthenticationConverter` overrides the claim name to
`authorities` and clears the prefix so values arrive verbatim (`ROLE_ADMIN`, `ROLE_CUSTOMER`). It
only works because auth-service's `OAuth2TokenCustomizer` puts that claim on the token in the first
place; the two are a matched pair, and changing the claim name on one side breaks the other
silently.

**The empty authority prefix looks wrong and must stay.** Now that the claim holds only roles it is
tempting to set the prefix to `ROLE_` — but auth-service already applies it before the value goes on
the wire, so that yields `ROLE_ROLE_ADMIN` and 403s every admin write. Either both sides carry the
prefix (as today) or neither does.

**`issuer-uri` must equal the token's `iss` claim exactly**, or every request 401s. auth-service
currently derives its issuer from the request host, so the `localhost:8076` default only lines up
for host runs. See `auth-service/CLAUDE.md` — the issuer needs pinning before this works in Docker
or through the gateway.

> **Do not "fix" those 401s by switching to `jwk-set-uri`.** It makes them disappear — because it
> removes the `iss` check that was failing, not because the mismatch is resolved. The service would
> then accept tokens minted under any issuer that shares the key set. Pin auth-service's issuer
> instead; that is the actual fix.

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