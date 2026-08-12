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
- Flyway via **`spring-boot-starter-flyway`** + `flyway-database-postgresql` — `flyway-core` alone no
  longer auto-configures migrations.
- No Kafka and no direct Jackson use, so the Jackson 2 → 3 migration does not affect this service.

## Configuration

Source-tree `application.yml` carries only bootstrap config (`spring.application.name: user-service`,
the config-server import, and the Config Server URI). Port, schema, datasource, and everything else
come from `webstore-config/config/user-service.yml` + `application.yml` at startup — do not duplicate
them here. See `webstore-config/CLAUDE.md`.
