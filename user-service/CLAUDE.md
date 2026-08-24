# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

user-service owns **user accounts, profiles, addresses, and roles** for the webstore system.
It is a plain CRUD service — no Kafka, no outbound REST calls, no outbox/inbox.

- **Port:** `8075` (`${SERVICE_PORT:8075}` in `webstore-config/config/user-service.yml`)
- **Schema:** `user_schema`
- **Gateway route:** `/user/**` (prefix stripped via `RewritePath`)
- **Base package:** `userservice`

> ⚠️ **Overlaps with auth-service.** This service has a `users` table with `user_name` / `password` /
> `is_active` and a `security_role` table. auth-service has its own `users` in `auth_schema`, with
> its own credentials and its own `role` column. **Two systems of record for the same people, and
> nothing reconciles them.** Before adding authentication behaviour here, check
> `auth-service/CLAUDE.md` — which service owns credentials is an open decision.
>
> Both services now define a `RoleType` enum with the same constants (`ADMIN`, `CUSTOMER`) and the
> same one-role-per-user cardinality. The two happen to agree today; nothing keeps them in step.
> auth-service stores its role as an enum column rather than a `SecurityRole` entity — it has no
> equivalent of this service's `SecurityRoleController`, so there was nothing to join for.

## Build and Run

```bash
./gradlew :user-service:build
./gradlew :user-service:bootRun
./gradlew :user-service:test
```

Host runs need `DB_USERNAME` / `DB_PASSWORD` (values must match `secrets/postgres_*.txt`).
config-service must be up first.

## Architecture

Standard layered pattern used across the webstore services:

```
Controllers (@RestController)
    ↓
Managers (@Service — business logic, @Transactional boundary)
    ↓
Repositories (Spring Data JPA)
    ↓
Entities (JPA/Hibernate) → PostgreSQL
```

```
userservice/
├── UserServiceApplication.java
├── configs/        SecurityConfig
├── controllers/    UserController, UserProfileController, SecurityRoleController,
│                   RestExceptionHandler
├── managers/       UserManager, UserProfileManager, SecurityRoleManager
├── repositories/
├── entities/       User, UserProfile, Address, SecurityRole, RoleType, CoreEntity
├── dto/
├── mappers/        MapStruct, componentModel = "spring"
└── validators/
```

### Domain model

```
User (users)
└── SecurityRole (many-to-one, LAZY, required)

UserProfile (user_profile)
├── User    (one-to-one, LAZY, required, unique, cascade ALL + orphanRemoval)
└── Address (address)
```

`RoleType` is an enum: `ADMIN`, `CUSTOMER`.

All entities extend `CoreEntity` (`id` + `@Version version`) and use sequence generators with
`allocationSize = 50` (`user_seq`, `user_profile_seq`, `address_seq`, `security_role_seq`).

> `UserProfile` cascades `ALL` with `orphanRemoval = true` onto `User` — deleting a profile deletes
> the underlying user row. Check that this is intended before adding a profile-delete endpoint.

## REST API

| Controller | Base path | Operations |
|---|---|---|
| `UserController` | `/v1/users/user` | `POST`, `GET /{id}`, `PUT`, `DELETE /{id}` |
| `UserProfileController` | `/v1/users/profile` | `POST`, `GET /{id}`, `PUT`, `DELETE /{id}` |
| `SecurityRoleController` | `/v1/users/role` | `GET`, `PUT /{userID}` |

Through the gateway these become `/user/v1/users/...` — the route strips only the `/user` prefix.

`RestExceptionHandler` centralises error responses; add new exception mappings there rather than
try/catch in controllers.

**Swagger UI:** `http://localhost:8075/swagger-ui.html` · **OpenAPI JSON:** `/v3/api-docs`
(springdoc is enabled in DEV and UAT, disabled in PROD via the config repo's `application-prod.yml`).

## Security (OAuth2 Resource Server)

**Every endpoint requires `ADMIN` or `SERVICE`.** Unlike product-service — which publishes its
catalog reads — nothing here is public: accounts, profiles, addresses, and role assignments are
personal data.

| Path | Access |
|---|---|
| `/actuator/health/**`, `/actuator/prometheus` | public |
| `/swagger-ui/**`, `/v3/api-docs/**` | public (springdoc is disabled entirely in PROD) |
| everything else — all three controllers | `hasAnyRole("ADMIN", "SERVICE")` |

The two infrastructure carve-outs are not optional: the Compose healthcheck curls
`/actuator/health` and Prometheus scrapes `/actuator/prometheus` every 15 s.

**No webstore service calls this one today**, so these rules break no existing traffic. `SERVICE` is
granted anyway, for symmetry and for the first caller that needs it (an order enriching a shipping
address, say) — such a caller would authenticate with a `client_credentials` token the same way
order-service already does for inventory and payment.

Configured in `configs/SecurityConfig.java` — a **new package**; this service had no `configs/`
before. The issuer comes from `spring.security.oauth2.resourceserver.jwt.issuer-uri` in the config
repo's `user-service.yml` (`${AUTH_ISSUER_URI:http://localhost:8076}`). Validation is **local** — the
JWKS is discovered once, lazily, and cached; auth-service is not in the request path.

### Two consequences of the blanket rule

**A `CUSTOMER` cannot read their own profile.** `GET /v1/users/profile/{id}` needs `ADMIN` or
`SERVICE`, so there is no self-service path. The fix, when wanted, is an **ownership** rule rather
than another role — match the caller's identity against the requested id. That is what
product-service's `authUserId` claim exists for, but mind the mismatch first: the claim carries
`auth_schema.users.id`, and this service's ids come from `user_schema.users`. Those are different
numbers for the same person until the stores are reconciled.

**Nobody can self-register.** `POST /v1/users/user` is admin-only. Consistent with the platform
today — auth-service has no registration endpoint either, and its only account comes from the
dev-profile seeder — but the two have to be solved together rather than separately. See the
duplicate-user-store question in `auth-service/CLAUDE.md`.

> ⚠️ **`PUT /v1/users/role/{userID}` does not change what a token says.** Token roles come from
> `auth_schema.users.role`; `security_role` here is a separate record of the same idea. Until the
> stores are reconciled, that endpoint edits a local copy — a user promoted to `ADMIN` here still
> gets `ROLE_CUSTOMER` in their next token, and every `hasRole("ADMIN")` rule across the platform
> keeps refusing them.

The claim-name + empty-prefix pair is identical to the other resource servers', and carries the
identical trap: values arrive already prefixed (`ROLE_ADMIN`), so setting the prefix to `ROLE_`
yields `ROLE_ROLE_ADMIN` and 403s everything. See `auth-service/CLAUDE.md` for the full matched-pair
explanation.

## Database

Flyway migrations in `src/main/resources/db/migration/`:

| Migration | Purpose |
|---|---|
| `V1__init_tables.sql` | `address`, `security_role`, `user_profile`, `users` + sequences |
| `V2__alter_role_able.sql` | role table change |
| `V3__change_adress_relation.sql` | address relationship rework |
| `V4__changing_address_code.sql` | address column change |
| `V5__core_entity_refactor.sql` | align entities with the shared `CoreEntity` base |

`ddl-auto: validate` — Flyway is authoritative. Entity/schema drift is fatal at startup. Never modify
an applied migration; add a new one with the next version number.

## Conventions

- **DTO pattern** — separate DTOs for API contracts, MapStruct mappers (`componentModel = "spring"`).
- **`@Transactional` at the manager layer**, not on controllers or repositories.
- **Optimistic locking** via `@Version` on every entity.
- **Validation** — Bean Validation on DTOs, plus dedicated classes in `validators/`.
- **Datafaker** is a dependency (`net.datafaker:datafaker`) for test-data generation.

## Spring Boot 4 notes

- Uses **`spring-boot-starter-webmvc`** (renamed from `spring-boot-starter-web` in Boot 4).
- Security comes from **`spring-boot-starter-oauth2-resource-server`** (not `-starter-security` — the
  resource-server starter pulls in `spring-security-config`/`-web` plus `-oauth2-jose`).
- Flyway via **`spring-boot-starter-flyway`** + `flyway-database-postgresql` — `flyway-core` alone no
  longer auto-configures migrations.
- No Kafka and no direct Jackson use, so the Jackson 2 → 3 migration does not affect this service.

## Configuration

Source-tree `application.yml` carries only bootstrap config (`spring.application.name: user-service`,
the config-server import, and the Config Server URI). Port, schema, datasource, and everything else
come from `webstore-config/config/user-service.yml` + `application.yml` at startup — do not duplicate
them here. See `webstore-config/CLAUDE.md`.
