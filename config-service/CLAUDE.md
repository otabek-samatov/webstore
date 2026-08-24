# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service Role

config-service is the **Spring Cloud Config Server** for the webstore system. Every other service
fetches its runtime configuration from here on startup. It is the **first** service that must be
running — without it, no other service can resolve its config.

- **Spring `application.name`:** `config-server`
- **Port:** `8071`
- **Entry point:** `ConfigServerApplication` — a `@SpringBootApplication` annotated `@EnableConfigServer`.
- Other services reach it directly at `http://localhost:8071` (containers use
  `http://config-service:8071` via the `SPRING_CLOUD_CONFIG_URI` env var), configured via their
  `spring.config.import: optional:configserver:` + `spring.cloud.config.uri`.

## Build & Run

```bash
./gradlew :config-service:build       # build
./gradlew :config-service:bootRun     # run (start this FIRST)
./gradlew :config-service:test        # tests (context-load only)
```

Health/info: `http://localhost:8071/actuator/health` (all actuator endpoints are exposed).

To inspect served config directly:
`GET http://localhost:8071/<application>/<profile>` — e.g.
`http://localhost:8071/inventory-service/default` returns the merged property sources
(`application.yml` + `inventory-service.yml`). Request a real profile to see the environment overlays,
e.g. `http://localhost:8071/order-service/prod` merges
`application.yml` + `application-prod.yml` + `order-service.yml` + `order-service-prod.yml`
(the DEV/UAT/PROD overlays live in the `webstore-config` repo; profile selected at runtime via
`SPRING_PROFILES_ACTIVE`, driven by `SPRING_PROFILE` in the `webstore` repo's `.env` files).

## Configuration Backend (Git)

Configured entirely in `src/main/resources/application.yml`:

```yaml
spring.cloud.config.server.git:
  uri: https://github.com/otabek-samatov/webstore-config
  searchPaths: config        # config files live under the repo's config/ folder
  default-label: master      # branch served when the client doesn't request one
  timeout: 5
  clone-on-start: true       # clone the repo at startup (fail fast if unreachable)
  force-pull: true           # discard local changes and hard-pull on refresh
```

- **The server reads from the Git remote, not any local clone.** A config change only takes effect
  once it is committed and **pushed** to the `webstore-config` repo (and the client refreshes or
  restarts). The local working copy at `C:\Data\Projects\webstore-config` is for editing convenience only.
- `searchPaths: config` is why the YAML files live under `config/` in that repo.
- `default-label: master` — the served branch. If the repo's default branch is ever renamed, update
  this.
- Config matching is by the client's `spring.application.name` → `<name>.yml`, merged on top of the
  shared `application.yml`. See the `webstore-config` repo's own `CLAUDE.md`.

## Gotchas

- **Application name is `config-server`, not `config-service`.** The directory/module is
  `config-service`; the Spring application name is `config-server`. There is no `config-server.yml`
  in the config repo because the server does not fetch config from itself.

## Dependencies

- `spring-cloud-config-server` (the server itself)
- `spring-boot-starter-actuator`
- Spring Cloud BOM `2025.1.2`, Spring Boot `4.1.0` (Spring Framework 7), Java 25

> The formerly vestigial `spring-boot-starter-data-jpa` + `postgresql` dependencies and the hardcoded
> `spring.datasource` block were removed — the config backend is Git, and a config server needs no
> database.

## Startup Position

**Start config-service first**, before all other services — they block on config resolution at boot.
config-service itself depends on nothing in the system except network access to the
`webstore-config` Git remote.
