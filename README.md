# Webstore

Webstore is a microservices-based e-commerce backend for selling books. It is a multi-module Gradle
monorepo with 7 independent Spring Boot services. Backend only — there is no UI.

## Services

| Service               | Port | Purpose                                            | Database schema    |
|-----------------------|------|----------------------------------------------------|--------------------|
| **config-service**    | 8071 | Centralized configuration (Spring Cloud Config)    | —                  |
| **gateway-service**   | 8072 | API gateway (Spring Cloud Gateway Server Web MVC)  | —                  |
| **product-service**   | 8073 | Book catalog: authors, publishers, categories      | `product_schema`   |
| **inventory-service** | 8074 | Stock levels and reservation tracking              | `inventory_schema` |
| **user-service**      | 8075 | User registration, profiles, roles                 | `user_schema`      |
| **order-service**     | 8077 | Order placement and tracking (orchestration saga)  | `order_schema`     |
| **payment-service**   | 8078 | Payment processing and refunds                     | `payment_schema`   |

All schemas live in one shared PostgreSQL database (`webstore`), one schema per service, migrated by
Flyway.

## Tech stack

- Java 25, Gradle 9 (multi-module monorepo)
- Spring Boot 4.1 (Spring Framework 7), Spring Cloud 2025.1
- PostgreSQL 18
- Apache Kafka 4.3 (KRaft, single node) — event-driven messaging with the **transactional
  outbox / inbox** pattern (no Kafka transactions)
- Spring Cloud Config — Git-backed configuration served from
  [webstore-config](https://github.com/otabek-samatov/webstore-config)
- Spring Cloud Gateway (servlet stack) — single entry point on port 8072
- Spring Data JPA (Hibernate), Flyway, MapStruct, Lombok

## Architecture notes

- **No service discovery.** Services reach each other at direct URLs: Docker Compose DNS names +
  fixed ports in containers, `localhost` + fixed ports on host runs. URLs are configured as
  `${*_SERVICE_URL:http://localhost:<port>}` placeholders in the config repo and overridden per
  environment via env vars. In Kubernetes they map onto K8s Services, which also load-balance.
- **Synchronous REST**: order-service calls inventory-service (prices, stock reservation) and
  payment-service (charging) directly.
- **Asynchronous Kafka**: order-service publishes stock commit/release events consumed by
  inventory-service; payment-service publishes payment-status events consumed by order-service.
  Producers write events to an `outbox_events` table in the same DB transaction as the business
  change; consumers dedup via an `inbox_messages` table.
- **Order creation** runs as an orchestration-based saga with compensating actions (price → reserve
  stock → persist order → charge payment).

## Running locally

Prerequisites: Docker. Create the gitignored secrets files first (one value per file):

```
secrets/postgres_user.txt
secrets/postgres_password.txt
```

Run the full stack (infrastructure + all 7 services):

```bash
docker compose up -d --build
```

The API is then available through the gateway at `http://localhost:8072`
(`/product/**`, `/inventory/**`, `/order/**`, `/payment/**`, `/user/**`).

Run only the infrastructure and start services on the host instead:

```bash
docker compose up -d postgres kafka
DB_USERNAME=... DB_PASSWORD=... ./gradlew :config-service:bootRun   # first
DB_USERNAME=... DB_PASSWORD=... ./gradlew :order-service:bootRun    # then any service
```

Host-run services need `DB_USERNAME` / `DB_PASSWORD` env vars matching `secrets/*.txt`, and
config-service must be up before the others.

## Configuration

All runtime configuration (ports, schemas, Kafka topics, service URLs, gateway routes) lives in the
[webstore-config](https://github.com/otabek-samatov/webstore-config) repo and is served by
config-service **from the Git remote** — changes take effect only after commit + push.

## Not yet implemented

- Auth service (Spring Security with JWT/OAuth2)
- Kubernetes deployment (the stack currently runs via Docker Compose)
- Comprehensive integration tests
- API documentation (Swagger/OpenAPI)
