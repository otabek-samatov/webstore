# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Webstore is a microservices-based e-commerce backend application for selling books. This is a **multi-module Gradle
monorepo** with 7 independent microservices.

**Technology Stack:**

- Java 25 (Gradle toolchain in root `build.gradle`)
- Spring Boot 4.1.0 (built on Spring Framework 7)
- Spring Cloud 2025.1.2 (Oakwood)
- PostgreSQL 18 (Docker container `postgres:18.4`, see [Local Infrastructure](#local-infrastructure))
- Apache Kafka 4.3 (Docker container `apache/kafka:4.3.1`, KRaft single node)
- Spring Cloud Config (Centralized Configuration)
- Spring Cloud Gateway Server Web MVC (API Gateway)

> **No service discovery.** Eureka (and the old discovery-service module) was removed. Services reach
> each other at **direct URLs**: Docker Compose DNS names + fixed ports in containers
> (`http://inventory-service:8074`), `localhost` + fixed ports on host runs. The URLs are
> property-driven with per-environment env-var overrides (`*_SERVICE_URL`); in Kubernetes they will
> map onto K8s Service names, which also provide the load balancing.

> **Spring Boot 4 upgrade notes (applies repo-wide):** every module now pins Spring Boot `4.1.0` and
> Spring Cloud `2025.1.2`. Key migration deltas baked into the build:
> - Jackson 2 → **Jackson 3** — `ObjectMapper` moved to `tools.jackson.databind`; serialization throws
    > the unchecked `tools.jackson.core.JacksonException` (no more checked `JsonProcessingException`).
> - **Flyway** is no longer auto-configured by `flyway-core` alone — services depend on the new
    > `spring-boot-starter-flyway` plus `flyway-database-postgresql` (versions BOM-managed, ~Flyway 11).
> - `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**.
> - Gateway starter `spring-cloud-starter-gateway-mvc` → **`spring-cloud-starter-gateway-server-webmvc`**.
> - Spring Kafka's `JsonSerializer`/`JsonDeserializer` (deprecated for removal, Jackson-2-backed) →
    > **`JacksonJsonSerializer`/`JacksonJsonDeserializer`** (Jackson 3).

**Note:** This is backend-only - no UI implementation.

## Build Commands

This is a Gradle-based multi-module project using Gradle 9.6.0 (wrapper; Spring Boot 4 requires
Gradle 8.14+ on the 8.x line, or Gradle 9.x).

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :order-service:build
./gradlew :product-service:build

# Run tests for all services
./gradlew test

# Run tests for specific service
./gradlew :inventory-service:test

# Clean build
./gradlew clean build

# Run a specific service
./gradlew :order-service:bootRun
./gradlew :gateway-service:bootRun

# Skip tests during build
./gradlew build -x test

# Build without daemon (useful for CI/CD)
./gradlew build --no-daemon
```

## Microservices Architecture

### Service Inventory

The system consists of 8 microservices defined in `settings.gradle`:

| Service               | Port | Purpose                                                                              | Database                        |
|-----------------------|------|--------------------------------------------------------------------------------------|---------------------------------|
| **config-service**    | 8071 | Centralized configuration via Spring Cloud Config                                    | N/A                             |
| **gateway-service**   | 8072 | API Gateway using Spring Cloud Gateway MVC                                           | N/A                             |
| **product-service**   | 8073 | Book catalog with authors, publishers, categories                                    | PostgreSQL (`product_schema`)   |
| **inventory-service** | 8074 | Stock level management and reservation tracking                                      | PostgreSQL (`inventory_schema`) |
| **user-service**      | 8075 | User accounts, profiles, addresses, roles                                            | PostgreSQL (`user_schema`)      |
| **auth-service**      | 8076 | OAuth 2.1 / OIDC authorization server (**incomplete** — see its `CLAUDE.md`)         | PostgreSQL (`auth_schema`)      |
| **order-service**     | 8077 | Order placement and tracking                                                         | PostgreSQL (`order_schema`)     |
| **payment-service**   | 8078 | Payment processing and refunds                                                       | PostgreSQL (`payment_schema`)   |

> ⚠️ **user-service and auth-service both store users.** user-service owns `user_schema.users` +
> `security_role`; auth-service owns `auth_schema.users` + `users_authorities` with its own
> credentials. Two systems of record for the same people, and nothing reconciles them — which
> service owns credentials is an **open architectural decision**. See `auth-service/CLAUDE.md`.

> Ports and schema names above are the **defaults** from `webstore-config/config/<service>.yml`. Multi-instance
> deployments override `server.port` per instance to avoid a local port clash, but there is **no**
> Kafka-transactional-ID uniqueness requirement — instances coordinate at the row level via the
> outbox/inbox tables (see Kafka section).

**Service-Specific Documentation:** Each service has its own `CLAUDE.md` file in its directory with detailed
implementation guidance.

### Startup Order

Services must be started in this order for proper operation:

1. **Infrastructure** (`docker compose up -d` — see [Local Infrastructure](#local-infrastructure)):
    - PostgreSQL (5432)
    - Kafka Broker (9092)
2. **config-service** (8071) - Required by all other services for configuration
3. **Business Services** (any order):
    - product-service (8073), inventory-service (8074), user-service (8075)
   - order-service (8077), payment-service (8078)
4. **gateway-service** (8072) - API Gateway (routes are static, so it only needs config-service to
   boot; requests to a backend that isn't up yet fail until that service starts)

<a id="local-infrastructure"></a>### Local Infrastructure & Containers (Docker Compose)

`docker-compose.yml` in the repo root runs the **entire stack** — infrastructure plus all 8 Spring Boot
services:

```bash
docker compose up -d --build          # build all service images + start everything (DEV, default .env)
docker compose up -d postgres kafka   # infrastructure only (services run on host)

docker compose --env-file .env.uat  up -d   # UAT  environment
docker compose --env-file .env.prod up -d   # PROD environment
```

The environment is selected by the `.env` file in play (see [Environments &
profiles](#environments-profiles)): the default `.env` is DEV; `.env.uat` / `.env.prod` carry the same
variables plus their own `SPRING_PROFILE`, which Compose passes to every container as
`SPRING_PROFILES_ACTIVE` so Config Server serves the matching `application-<profile>.yml` /
`<service>-<profile>.yml` overlays.

**Infrastructure:**

- **postgres** — `postgres:18.4`, container `webstore-postgres`, port `5432`, named volume mounted at
  `/var/lib/postgresql` (PG 18+ volume layout). Credentials come from **Docker secrets**, not env vars:
  `POSTGRES_USER_FILE` / `POSTGRES_PASSWORD_FILE` point at `/run/secrets/postgres_user` /
  `/run/secrets/postgres_password`.
- **kafka** — `apache/kafka:4.3.1`, container `webstore-kafka`, KRaft combined mode (single node),
  **two client listeners**: `PLAINTEXT` advertised as `localhost:9092` (host-run apps) and `INTERNAL`
  advertised as `kafka:19092` (containerized services).

**Monitoring (Prometheus + Grafana):** two additional containers, optional for the stack itself
(no service depends on them); config lives under `./monitoring/`:

- **prometheus** — `prom/prometheus:v3.5.0`, container `webstore-prometheus`, host port `9090`
  (`PROMETHEUS_PORT`), 15-day retention on the `prometheus-data` volume. Scrapes every service's
  `/actuator/prometheus` endpoint every 15 s using **container DNS names + internal ports**
  (`./monitoring/prometheus/prometheus.yml`; one scrape job per service, so the `job` label is the
  service name). ⚠️ Compose does **not** substitute `${VAR}` inside mounted files — the target ports
  in `prometheus.yml` are hardcoded and must be kept in sync with `.env` if a service port changes.
- **grafana** — `grafana/grafana:12.0.0`, container `webstore-grafana`, host port `3000`
  (`GRAFANA_PORT`), data on the `grafana-data` volume. Auto-provisions the Prometheus datasource
  (uid `prometheus`, at `http://prometheus:9090`) and every dashboard JSON in
  `./monitoring/grafana/dashboards/` (seeded with `webstore-services.json` — per-service HTTP
  rate/latency/5xx, JVM heap/CPU/GC/threads, HikariCP, log-event panels, switched by a `$service`
  variable). Credentials default to `admin`/`admin` — **DEV only**; override via
  `GF_SECURITY_ADMIN_*` (e.g. `GF_SECURITY_ADMIN_PASSWORD__FILE` + a Docker secret) for UAT/PROD.

**Accessing the monitoring UIs** (with the stack up; all host ports come from the active `.env`
file — see [Port configuration](#port-configuration)):

- **Prometheus:** `http://localhost:${PROMETHEUS_PORT}` (default `9090`) — query UI at `/query`,
  scrape-target health at `/targets` (all 8 service jobs should show **UP** — ⚠️ auth-service has
  no scrape job yet, so it will not appear).
- **Grafana:** `http://localhost:${GRAFANA_PORT}` (default `3000`) — log in with `admin` / `admin`
  (DEV default), then **Dashboards → Webstore → Webstore Services**; pick the service with the
  `$service` dropdown. The datasource and dashboard are provisioned automatically — no manual
  setup on first login.
- **Raw metrics of one service:** `http://localhost:${<NAME>_SERVICE_PORT}/actuator/prometheus`
  (e.g. `http://localhost:8077/actuator/prometheus` for order-service with the default
  `ORDER_SERVICE_PORT`).

Metrics come from `micrometer-registry-prometheus` (a `runtimeOnly` dependency in **every** service's
`build.gradle`) + actuator. The `prometheus` endpoint is exposed in all profiles: DEV exposes `*`, and
the UAT/PROD overlays in the config repo include `prometheus` in their trimmed actuator surface. On
host runs the endpoint is at `http://localhost:<port>/actuator/prometheus` (the Docker Prometheus
can't scrape host-run services — its targets are container DNS names).

**Service images:** all 8 services build from the **shared root `Dockerfile`** (multi-stage: Gradle
wrapper build on `eclipse-temurin:25-jdk`, runtime on `eclipse-temurin:25-jre` + curl for healthchecks),
selected via the `SERVICE` build arg that compose passes per service. `.dockerignore` excludes
`secrets/` so credentials can never enter an image layer. Startup ordering is enforced with
healthchecks + `depends_on: service_healthy` (postgres/kafka → config-service →
business services; gateway-service waits only on config-service).

**Container environment wiring** (plain env vars for non-secret endpoints, resolved by placeholders
served from the config repo):

- `SPRING_CLOUD_CONFIG_URI=http://config-service:8071` — overrides the source-tree `localhost:8071`
- `SPRING_PROFILES_ACTIVE=${SPRING_PROFILE:-dev}` — declared in the `x-spring-env` anchor so it reaches
  every service; driven by `SPRING_PROFILE` in the active `.env` file. Selects the profile overlays
  Config Server serves (`application-<profile>.yml` / `<service>-<profile>.yml`). See
  [Environments & profiles](#environments-profiles)
- `KAFKA_BROKERS=kafka:19092` — feeds `${KAFKA_BROKERS:...}` (the INTERNAL listener)
- `DB_HOST=postgres` — feeds `${db.host:...}` in the datasource URL
- The five `*_SERVICE_URL` vars — declared **once** in the `x-service-urls` YAML anchor and merged
  into the services that call or route to others (order-service, gateway-service). They feed the
  `${*_SERVICE_URL:http://localhost:<port>}` placeholders in the config repo, pointing REST calls /
  gateway routes at the container DNS names; entries a consumer doesn't reference are ignored
- `SERVICE_PORT` — each service container gets this set to **its own** port; it feeds the
  `server.port: ${SERVICE_PORT:<default>}` placeholder in the config repo (config-service reads it
  from its **source** `application.yml` instead, since it isn't configured from the config repo).
  See [Port configuration](#port-configuration) below
- DB credentials arrive as **secrets** (`db_username` / `db_password` targets), not env vars

<a id="port-configuration"></a>**Port configuration (`.env` — single source of truth):** every port
used by the Compose stack lives in **`.env`** at the repo root (committed; not secret). Compose
auto-loads it and substitutes the `${*_PORT}` placeholders throughout `docker-compose.yml` — host port
mappings, healthcheck URLs, the `*_SERVICE_URL` targets, `SPRING_CLOUD_CONFIG_URI`, `KAFKA_BROKERS`,
and the Kafka listener/advertised/controller ports. Vars: `SPRING_PROFILE`, `CONFIG_SERVICE_PORT`,
`GATEWAY_SERVICE_PORT`, `PRODUCT_SERVICE_PORT`, `INVENTORY_SERVICE_PORT`, `USER_SERVICE_PORT`,
`ORDER_SERVICE_PORT`, `PAYMENT_SERVICE_PORT`, `POSTGRES_PORT`, `KAFKA_PORT`, `KAFKA_INTERNAL_PORT`,
`KAFKA_CONTROLLER_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`.

> The per-service `*_SERVICE_PORT` value is also passed **into** each container as the uniform
> `SERVICE_PORT` env var, so the container-internal `server.port` (served from the config repo) tracks
> `.env` too — change a port once in `.env` and both the host mapping and the app's port move together.
> Two caveats: (1) postgres is mapped `${POSTGRES_PORT}:5432` (host side only — the image always
> listens on 5432 internally); (2) the port **defaults** baked into the `*_SERVICE_URL` /
> `services.*.url` fallbacks in the config repo are host-run defaults and are **not** driven by `.env`
> (Compose can't reach the config repo, and host runs don't load `.env`).

<a id="environments-profiles"></a>**Environments & profiles (DEV / UAT / PROD):** there are two
per-environment layers, switched together by picking a `.env` file:

1. **Compose / infra layer — one `.env` file per environment** (`.env` = DEV, `.env.uat`, `.env.prod`).
   Because `docker compose --env-file <file>` **replaces** (does not merge) the default `.env`, each
   file is a **complete** copy of all Compose vars plus its own `SPRING_PROFILE`. Plain
   `docker compose up` uses `.env`, so there is deliberately no separate `.env.dev`.
2. **Spring application layer — profile overlays in the config repo.** Each `.env` sets `SPRING_PROFILE`,
   which Compose forwards as `SPRING_PROFILES_ACTIVE` to every container. Config Server then merges
   profile files on top of the base ones — precedence low→high:
   `application.yml` → `application-<profile>.yml` → `<service>.yml` → `<service>-<profile>.yml`.
   The shared overlays `application-{dev,uat,prod}.yml` exist today (e.g. PROD trims the actuator
   surface, disables SQL logging, sizes Kafka for multiple brokers); add `<service>-<profile>.yml` for
   service-specific per-env deltas (`order-service-prod.yml` is the seeded example).

> Caveats: (a) the profile files live in the **separate** `webstore-config` repo and only take effect
> once committed & pushed (Config Server reads Git, not the working copy); (b) `DB_HOST` /
> `KAFKA_BROKERS` are still hardcoded to the Compose containers in `docker-compose.yml` — if UAT/PROD
> use external managed DB/Kafka, parameterize those (and likely drop the `postgres`/`kafka` services
> via a `docker-compose.prod.yml` override); (c) `replication.factor` 2/3 in the UAT/PROD overlays
> require a matching broker count or `NewTopic` auto-creation fails.

**Secrets:** the `secrets:` section maps file-backed secrets from `./secrets/` (**gitignored** — never
commit): `secrets/postgres_user.txt` and `secrets/postgres_password.txt`, one value per file. These are
the single source of truth for the DB credentials; the services and the healthcheck read them at runtime
(see Configuration Management below for how services resolve them).

> Postgres only reads the secrets on **first initialization** of an empty data volume. To change the
> password later: `ALTER USER` inside the container, update the secret file, recreate the container,
> and update the credential source the services use (env vars on host / mounted secrets in Docker).

## System Architecture

### Communication Patterns

**1. Synchronous Communication (REST):**

- Services use a plain `RestClient` (Spring Framework) with **direct, property-configured URLs** —
  no service discovery, no client-side load balancing
- order-service (the only REST caller) resolves its targets from `services.inventory.url` /
  `services.payment.url` (defaults `http://localhost:<port>`; Docker overrides via
  `INVENTORY_SERVICE_URL` / `PAYMENT_SERVICE_URL` env vars)

**Key Inter-Service REST Calls:**

- Order Service → Inventory Service (price lookup + stock reservation during order creation /
  item add):
    - `POST {services.inventory.url}/v1/inventory/prices`
    - `POST {services.inventory.url}/v1/inventory/reserve-stock`
- Order Service → Payment Service (charge on order creation / payment retry):
    - `POST {services.payment.url}/v1/payments`

> Order creation does **not** fetch a cart — order items are supplied directly in the
> `CreateOrderDto` request body by the caller.

**2. Asynchronous Communication (Kafka):**

- Event-driven messaging for decoupled operations
- **One delivery design across all business services: the transactional outbox / inbox pattern.**
  Producers use an *idempotent, non-transactional* Kafka producer and write outbound events to an
  `outbox_events` table in the same DB transaction as the business change; a poller publishes them
  asynchronously. Consumers dedup redeliveries via an `inbox_messages` table keyed by a stable
  `messageId`. **No** service uses a Kafka `transactional.id` (see
  [Kafka Configuration Details](#kafka-configuration-details)).
    - **payment-service** — producer only (outbox); publishes payment-status events. No consumer/inbox.
    - **order-service** — both outbox (stock events) and inbox (payment events).
    - **inventory-service** — consumer/inbox (stock events); a string producer is configured but unused.

**Kafka Topics & Event Flows:**

| Topic                  | Producer        | Consumer          | Event Type                                                      | Purpose                         |
|------------------------|-----------------|-------------------|-----------------------------------------------------------------|---------------------------------|
| `stock-status-event`   | order-service   | inventory-service | `StockStatusKafka` (producer) → `StockStatusMessage` (consumer) | Stock commit / release / revert |
| `payment-status-event` | payment-service | order-service     | `PaymentStatusMessage`                                          | Payment status updates          |

> Property keys in code are `topic.stock.status` / `topic.payment.status`; the **values** on the wire
> are `stock-status-event` / `payment-status-event` (set in `webstore-config/config/application.yml`).
> A `topic.order.status` (`order-status-event`) key still exists in config but is **no longer used** by
> payment-service or order-service — the payment→order channel moved to `topic.payment.status`.

**Stock Management Flow:**

1. Order placed → Order Service reserves stock **synchronously over REST**
   (`POST inventory-service/v1/inventory/reserve-stock`) — there is **no** Kafka "reserve" event.
2. Order cancelled / refunded / abandoned after a failed payment → Order Service publishes a
   **`release`** stock event (via its outbox) → Inventory Service frees the reservation (`releaseStock`).
3. Order completed (payment confirmed) → Order Service publishes a **`commit`** stock event →
   Inventory Service finalizes the sale (`commitStock`, decrements physical stock).

**Payment Flow:**

1. Payment processed → Payment Service publishes a `PaymentStatusMessage` on `payment-status-event`
   (via its transactional outbox).
2. Order Service consumes it and transitions the order status (`COMPLETED` / `REFUNDED`).

### Configuration Management

**Spring Cloud Config Server (port 8071):**

- Git-backed configuration: https://github.com/otabek-samatov/webstore-config
- Local clone (authoritative source for all runtime properties): **`C:\Data\Projects\webstore-config`**
- All services fetch configuration from Config Server on startup
- Database connections, Kafka topics, and service-specific properties externalized
- **No credentials in the config repo** — the datasource username/password are placeholders resolved
  per environment (see below)

**`webstore-config` repository layout:**

```
webstore-config/
└── config/
    ├── application.yml          # shared defaults applied to every service
    ├── application-dev.yml      # shared per-profile overlays (DEV / UAT / PROD)
    ├── application-uat.yml
    ├── application-prod.yml
    ├── gateway-service.yml      # per-service overrides (one file per service)
    ├── product-service.yml
    ├── inventory-service.yml
    ├── user-service.yml
    ├── order-service.yml
    ├── order-service-prod.yml   # per-service, per-profile overlay (example)
    └── payment-service.yml
```

Spring Cloud Config matches each service's `spring.application.name` to the corresponding `<name>.yml` file
and merges it on top of `application.yml`. When a profile is active (`SPRING_PROFILES_ACTIVE`, set per
environment — see [Environments & profiles](#environments-profiles)) it also merges the matching
`application-<profile>.yml` and `<service>-<profile>.yml` overlays. Full precedence, low→high:
`application.yml` → `application-<profile>.yml` → `<service>.yml` → `<service>-<profile>.yml`.
To change runtime config (Kafka topic names, partition count, ports, gateway routes, etc.), edit a file
under `C:\Data\Projects\webstore-config\config\` and commit; the Config Server serves the latest commit
from the configured Git remote.

**What lives in `application.yml` (shared by all services):**

- **Actuator:** `management.endpoints.web.exposure.include: "*"`
- **Datasource:** `jdbc:postgresql://${db.host:localhost}:${db.port:5432}/${db.name:webstore}?currentSchema=${service.schemaName}`,
  driver `org.postgresql.Driver`. Host/port/database have local-dev defaults, overridable via
  `DB_HOST` / `DB_PORT` / `DB_NAME` env vars (relaxed binding).
- **Datasource credentials:** `username: ${db_username}` / `password: ${db_password}` — **no literal
  credentials in the repo.** Resolution per environment:
    - **Host runs (today):** from `DB_USERNAME` / `DB_PASSWORD` environment variables (Spring relaxed
      binding maps `db_username` → `DB_USERNAME`). Values must match `webstore/secrets/*.txt`.
    - **Containerized runs (future):** from Docker secret files via each service's
      `optional:configtree:/run/secrets/` config import (files named `db_username` / `db_password`).
- **JPA/Hibernate:** `ddl-auto: validate` (Flyway is authoritative for schema), `show-sql: true`,
  PostgreSQL dialect
- **Kafka:** `bootstrap.servers: localhost:9092`, `num.partitions: 3`, `replication.factor: 1`
  (sized for a single-broker local Kafka). These are **custom top-level keys** read via `@Value`
  in each service's `KafkaConfig` — they are **not** the standard `spring.kafka.*` properties.
- **Kafka topics:** `topic.stock.status: stock-status-event`, `topic.payment.status: payment-status-event`.
  A legacy `topic.order.status: order-status-event` key still exists but is no longer used.

> Note: the Kafka topic names in the running config are `stock-status-event` / `order-status-event`,
> not `stock-status-topic` / `order-status-topic`. Older references to the `*-topic` names elsewhere in
> docs are stale.

**What lives in each `<service>.yml`:**

- `server.port` — `${SERVICE_PORT:<default>}`: the inline default (see Service Inventory table above)
  applies to host runs; containers override it with the `SERVICE_PORT` env var Compose injects from
  `.env` (see [Port configuration](#port-configuration))
- `service.schemaName` — PostgreSQL schema injected into the shared datasource URL
- Service-specific overrides (e.g., `gateway-service.yml` defines `spring.cloud.gateway.routes`;
  `order-service.yml` defines the `services.inventory.url` / `services.payment.url` REST targets)

**What lives in the profile overlays (`application-<profile>.yml` / `<service>-<profile>.yml`):** only the
per-environment **deltas** — everything else inherits from the base files. The shared overlays
(`application-{dev,uat,prod}.yml`) cover cross-cutting env differences (actuator exposure, SQL log level,
`num.partitions` / `replication.factor`); add a `<service>-<profile>.yml` (e.g. `order-service-prod.yml`)
only when one service needs an env-specific override the shared overlay can't express.

**Gateway routes (defined in `gateway-service.yml`):**

Route targets are direct URLs via `${*_SERVICE_URL:http://localhost:<port>}` placeholders — host-run
defaults on `localhost`, container values injected by Compose env vars:

| External path   | Target env var          | Docker value                    |
|-----------------|-------------------------|---------------------------------|
| `/auth/**`      | `AUTH_SERVICE_URL`      | `http://auth-service:8076`      |
| `/inventory/**` | `INVENTORY_SERVICE_URL` | `http://inventory-service:8074` |
| `/order/**`     | `ORDER_SERVICE_URL`     | `http://order-service:8077`     |
| `/payment/**`   | `PAYMENT_SERVICE_URL`   | `http://payment-service:8078`   |
| `/product/**`   | `PRODUCT_SERVICE_URL`   | `http://product-service:8073`   |
| `/user/**`      | `USER_SERVICE_URL`      | `http://user-service:8075`      |

Each route strips its prefix via `RewritePath=/<prefix>/(?<path>.*), /$\{path}` before forwarding.

> ⚠️ **Prefix stripping is a problem for `/auth/**`.** An authorization server advertises its own
> URLs, and auth-service currently derives its issuer from the incoming request — so through the
> gateway it publishes unreachable container URLs in `/.well-known/openid-configuration`, the `iss`
> claim, and the JWKS URI. Fixing it means pinning the issuer to the external URL via an
> `AuthorizationServerSettings` bean. Until then, reach auth-service directly on `localhost:8076`.
> `client_credentials` works through the gateway either way (single POST, no discovery or redirects).

**Application Properties Pattern (per service, in source tree):**

- Each service has `application.yml` with minimal bootstrap config:
  ```yaml
  spring:
    application:
      name: {service-name}
    config:
      import: "optional:configserver:,optional:configtree:/run/secrets/"
    cloud:
      config:
        uri: ${SPRING_CLOUD_CONFIG_URI:http://localhost:8071}
  ```
- The Config Server URL is a placeholder: `localhost:8071` is the host-run default; in a container
  Compose overrides it via the `SPRING_CLOUD_CONFIG_URI` env var (`http://config-service:8071`).
- The `optional:configtree:/run/secrets/` import (business services only) turns Docker secret files
  into properties when running in a container (`/run/secrets/db_username` → property `db_username`);
  on host runs the directory doesn't exist and the import is a silent no-op.
- Everything else (DB, Kafka, port, schema, etc.) is resolved from the Config Server at startup —
  do **not** duplicate those values into the service's source-tree `application.yml`.

**Editing config: workflow**

1. Edit the relevant file in `C:\Data\Projects\webstore-config\config\` (the base file, or the
   `application-<profile>.yml` / `<service>-<profile>.yml` overlay if the change is environment-specific).
2. Commit and push to the Git remote — Config Server reads from Git, not the local working copy.
3. Restart the affected service(s) or hit `/actuator/refresh` on a service with `@RefreshScope` beans.
   (A profile overlay only applies to services started with that `SPRING_PROFILES_ACTIVE`.)

### Database Architecture

**Database-per-Service Pattern:**

- Each business service has its own PostgreSQL schema
- Single shared PostgreSQL instance (localhost:5432/webstore)
- Flyway migrations in each service: `src/main/resources/db/migration/`

**Schema Overview:**

**Product Service:**

- `author`, `book`, `book_author`, `book_category`, `book_images`, `category`, `publisher`
- Supports multi-author books, hierarchical categories

**Inventory Service:**

- `inventory` with stock levels, reserved stock, version for optimistic locking
- `inventory_change` audit trail of every stock operation
- `inbox_messages` — consumer-side Kafka dedup (idempotency by `message_id`)

**User Service:**

- `users`, `user_profile`, `address`, `security_role`

**Order Service:**

- `orders`, `order_item` with status, shipping/tax calculations
- `outbox_events` — transactional outbox for outbound Kafka events
- `inbox_messages` — consumer-side dedup for inbound payment events
- `saga_instance` — orchestration-saga lifecycle/audit state for order creation

**Payment Service:**

- `payment`, `refund` — `refund` is now `@OneToOne` to `payment` (one refund per payment; no amount/status columns)
- `outbox_events` — transactional outbox for outbound payment-status Kafka events

### Common Architectural Patterns

All services follow consistent patterns:

**1. Layered Architecture:**

```
Controllers (@RestController)
    ↓
Managers (Business Logic with @Service)
    ↓
Repositories (Spring Data JPA)
    ↓
Entities (JPA/Hibernate)
    ↓
PostgreSQL Database
```

**2. DTO Pattern:**

- Separate DTOs for API contracts
- MapStruct 1.5.5.Final for entity-to-DTO mapping
- Mappers use `componentModel = "spring"`

**3. Package Structure (per service):**

```
{service}/
├── {ServiceName}Application.java
├── controllers/       # REST endpoints (@RestController)
├── managers/          # Business logic (@Service)
├── repositories/      # Data access (JpaRepository)
├── entities/          # JPA entities
├── dto/              # Data Transfer Objects
│   └── kafka/        # Kafka-specific DTOs
├── mappers/          # MapStruct interfaces
├── configs/          # Spring configurations (KafkaConfig, RestConfig)
├── validators/       # Input validation
└── exceptions/       # Custom exceptions
```

**4. Configuration Classes:**

- `KafkaConfig.java` - Kafka producer/consumer configuration with exactly-once semantics
- `RestConfig.java` - plain `RestClient` bean (order-service only — the sole service making
  outbound REST calls)

**5. Database Versioning:**

- Flyway for schema migrations
- Optimistic locking with `@Version` fields
- Sequences for ID generation

**6. Transaction Management:**

- `@Transactional` on service/manager methods
- Transactional outbox / inbox for reliable Kafka delivery (no Kafka transactions)
- Transactional boundaries at manager layer

## API Gateway & Service Addressing

**API Gateway (Spring Cloud Gateway Server Web MVC):**

- Single entry point for all client requests
- Routes to microservices at direct URLs (see the gateway routes table above)
- Configured via Config Server

**Service addressing (no discovery layer):**

- Services are reached by stable DNS name + fixed port: Compose service names inside Docker,
  `localhost` on host runs — in Kubernetes these become K8s Service names
- All cross-service URLs are properties with `${*_SERVICE_URL:...}` env-var overrides; nothing
  registers anywhere at runtime
- Load balancing across replicas is the platform's job (Compose DNS / K8s Services), not the client's

## API Documentation (Swagger/OpenAPI)

The 5 business services (product, inventory, user, order, payment) generate interactive API docs via
**springdoc-openapi** (`org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3` — the 3.x line is
required for Spring Boot 4; 2.x only supports Boot 3). Zero configuration: springdoc introspects the
Spring MVC controllers at runtime. config-service and gateway-service expose no business API and have
no springdoc dependency.

Per service (ports from `.env` — see [Port configuration](#port-configuration)):

- **Swagger UI:** `http://localhost:${<NAME>_SERVICE_PORT}/swagger-ui.html`
  (e.g. `http://localhost:8077/swagger-ui.html` for order-service)
- **OpenAPI JSON:** `http://localhost:${<NAME>_SERVICE_PORT}/v3/api-docs`
- **Through the gateway:** the existing routes proxy the spec too —
  `http://localhost:${GATEWAY_SERVICE_PORT}/<prefix>/v3/api-docs` (e.g. `/order/v3/api-docs`); the
  Swagger UI itself is best opened directly on each service (its asset paths don't survive the
  prefix rewrite).

**Per environment:** enabled in DEV and UAT; **disabled in PROD** — `application-prod.yml` in the
config repo sets `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`
(remember: config repo changes take effect only once committed & pushed).

## Kafka Configuration Details

### Delivery Semantics (transactional outbox / inbox)

All business services use the **transactional outbox / inbox** pattern — there is **no** Kafka
`transactional.id` and **no** `KafkaTransactionManager` anywhere. Delivery is **at-least-once** on the
wire; exactly-once *effects* come from the outbox (write the event in the same DB tx as the business
change) and the inbox (consumer-side dedup by `messageId`). See each service's `CLAUDE.md` for the
full design.

**Producer Configuration (idempotent, non-transactional):**

- `enable.idempotence=true`
- `acks=all` (all replicas must acknowledge)
- `retries=Integer.MAX_VALUE`
- `max.in.flight.requests.per.connection=5`
- Value serializer is `StringSerializer` — the outbox stores already-serialized JSON, sent as a `String`
- A poller (`OutboxPoller` / `OutboxEventProcessor`) claims `PENDING` rows with an atomic conditional
  `UPDATE` and performs the actual `KafkaTemplate.send`

**Consumer Configuration (order-service, inventory-service):**

- Consumer group: `{application-name}-group`
- `isolation.level=read_committed`, `enable.auto.commit=false` (manual offset management)
- RECORD-level acknowledgment mode; `@Transactional` listener (the inbox row, business change, and any
  outbox rows commit together, then the offset commits)
- Value deserializer is `JacksonJsonDeserializer<>(...)` (Spring Kafka 4 / Jackson 3) with a
  constructor-configured target type (the wire payload has no `__TypeId__` header since producers send
  with `StringSerializer`)

> payment-service is **producer-only** (no `@KafkaListener`); inventory-service configures an idempotent
> string producer for symmetry but does not currently publish.

### Multi-Instance Deployment

No service uses a Kafka `transactional.id`, so the old "unique port per instance for the transactional
ID" constraint **does not apply** to any service. Instances coordinate at the **row level** instead — the
outbox poller claims rows with an atomic conditional `UPDATE`, and the inbox dedups on the `messageId`
primary key — so all services are safe to run concurrently regardless of port. (Per-instance `server.port`
overrides are still needed only to avoid a local port clash.)

## Development Workflow

### Adding a New Microservice

1. Add service to `settings.gradle`
2. Create service directory with `build.gradle`
3. Follow standard package structure (controllers/managers/repositories/etc.)
4. Configure `application.yml` with service name and Config Server URI
5. Give it a port: add `<NAME>_SERVICE_PORT` to `.env`, set `server.port: ${SERVICE_PORT:<default>}`
   in its `<service>.yml` (config repo), and in `docker-compose.yml` map `SERVICE_PORT:
   ${<NAME>_SERVICE_PORT}` into the container + use `${<NAME>_SERVICE_PORT}` for the port mapping and
   healthcheck. If it must be reachable from other services or the gateway, also add a
   `${<NAME>_SERVICE_URL:http://localhost:<port>}` placeholder in the config repo plus the env var in
   `docker-compose.yml` (no service registry — addressing is static)
6. Add Flyway migrations in `src/main/resources/db/migration/`
7. Create service-specific `CLAUDE.md`

### Modifying Existing Services

**When adding new features:**

1. Review service-specific `CLAUDE.md` for architectural guidance
2. Follow existing patterns (DTOs, MapStruct, layered architecture)
3. Add Flyway migration if database changes are needed
4. Update Kafka configs if new event types are introduced
5. Use `@Transactional` for consistency

**When adding Kafka event types:**

1. Create DTO in `{service}/dto/kafka/` package
2. Add consumer factory and container factory in `KafkaConfig`
3. Use `@Transactional` on consumer methods
4. Add validation and null checks in handlers

**When modifying database entities:**

1. Create Flyway migration (next version number: `V2__description.sql`)
2. Update entity class
3. Update corresponding DTO
4. Update MapStruct mapper if needed
5. Consider optimistic locking (`@Version`) implications

### Testing

**Test Structure:**

- Unit tests in `src/test/java/{service}/`
- Use JUnit 5 and Mockito
- Spring Boot Test support with `@SpringBootTest`
- Mock external REST calls and Kafka interactions

**Running Tests:**

```bash
# All services
./gradlew test

# Specific service
./gradlew :order-service:test

# With coverage
./gradlew test jacocoTestReport
```

## Common Pitfalls & Troubleshooting

**1. Service Won't Start:**

- Ensure Config Server is running first (port 8071)
- Check Config Server can reach Git repository
- Verify PostgreSQL is running and accessible (`docker compose up -d postgres`)
- `Could not resolve placeholder 'db_username'` → the service was started without DB credentials.
  On host runs set the `DB_USERNAME` / `DB_PASSWORD` environment variables (values must match
  `secrets/*.txt`); in a container mount the Docker secrets as `db_username` / `db_password`.

**2. Kafka Issues:**

- Ensure Kafka broker is running
- If outbound events aren't published, check the `outbox_events` table — rows stuck in `PROCESSING`
  point at a send failure (the poller resets stuck rows to `PENDING`); confirm the producer is
  **non-transactional** (a stray `transactional.id` makes `send()` outside a tx throw)
- Verify topic creation with correct partitions/replication
- Check consumer group configuration and that producer/consumer field names + `actionType` casing match

**3. REST Call Failures:**

- Verify the target service is up and its URL property resolves correctly for the environment
  (`services.inventory.url` / `services.payment.url` in order-service; `*_SERVICE_URL` env vars in
  Docker — a `localhost` default leaking into a container means the env var isn't set)
- Remember containers use Compose DNS names + fixed ports; host runs use `localhost` + fixed ports

**4. Database Migration Errors:**

- Never modify existing Flyway migrations
- Always create new migration files with incremented version
- Test migrations on clean database first

**5. MapStruct Compilation Issues:**

- Clean and rebuild: `./gradlew clean build`
- Check annotation processor is configured
- Verify mapper interfaces are in `mappers` package

## Key Dependencies

All services share common dependencies defined in root `build.gradle`:

- **Spring Boot:** 4.1.0
- **Spring Cloud:** 2025.1.2
- **MapStruct:** 1.5.5.Final
- **Lombok:** Annotation processor
- **Flyway:** via `spring-boot-starter-flyway` + `flyway-database-postgresql` (versions BOM-managed)
- **Jackson:** 3 (`tools.jackson.*`)
- **PostgreSQL:** JDBC driver
- **Kafka:** Spring Kafka 4
- **JUnit:** 5.10.2

## External Dependencies

Required infrastructure for running webstore (both provided by the repo-root `docker-compose.yml` —
see [Local Infrastructure](#local-infrastructure)):

1. **PostgreSQL 18** (localhost:5432/webstore, container `postgres:18.4`)
2. **Apache Kafka 4.3** (localhost:9092, container `apache/kafka:4.3.1`, KRaft single node; topics
   are auto-created by the services' `NewTopic` beans)
3. **Git Repository** for Config Server: https://github.com/otabek-samatov/webstore-config

## Project Status

> For current development activity, consult git history (branches, recent commits, open PRs) rather
> than a hand-maintained list here — it stays accurate without manual upkeep.

**Not Yet Implemented:**

- **Platform-wide security.** auth-service issues tokens and **product-service is the first (and so
  far only) consumer** — its `GET /v1/books/**` reads are public while writes require a token whose
  holder has the `WRITE` authority (see `product-service/CLAUDE.md`). The other five business
  services and the gateway are still entirely unauthenticated. Extending this means adding
  `spring-boot-starter-oauth2-resource-server` + `issuer-uri` to each remaining service, a
  `JwtAuthenticationConverter` reading the `authorities` claim, and per-endpoint rules.
- **auth-service itself is incomplete** — no controllers, no way to create the first user, no tests,
  issuer unpinned, ephemeral signing key, in-memory client registry. See `auth-service/CLAUDE.md`.
- Kubernetes deployment (the full stack — infrastructure + all 8 services — already runs via
  `docker-compose.yml` and the shared root `Dockerfile`; the direct-URL addressing maps 1:1 onto
  K8s Services)
- Comprehensive integration tests
