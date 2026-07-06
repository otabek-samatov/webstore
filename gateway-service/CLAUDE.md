# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service Role

gateway-service is the **API Gateway** — the single external entry point that routes incoming
requests to the backend services at **direct, statically configured URLs** (there is no service
discovery in the system). It is built on **Spring Cloud Gateway Server Web MVC** (the servlet-stack
gateway, not the reactive WebFlux one).

> **Spring Cloud 2025.x rename:** the old `spring-cloud-starter-gateway-mvc` artifact was deprecated in
> 2025.0 and is gone in 2025.1 — this service now depends on **`spring-cloud-starter-gateway-server-webmvc`**.
> The route DSL/config is unchanged; only the starter coordinate moved. (The HTTP-client config that was
> nested under the old `mvc` namespace is now top-level `gateway` config, but this service doesn't set it.)

- **Entry point:** `ApiGatewayApplication` — a plain `@SpringBootApplication`.
- **Spring `application.name`:** `gateway-service` (matches its config file `gateway-service.yml`, so
  Spring Cloud Config serves it the port + routes correctly)
- **Port:** `8072` (from `gateway-service.yml`)

> **Naming convention:** like every other service, the module name, the bootstrap
> `spring.application.name`, and the config file name all line up (`gateway-service` →
> `gateway-service.yml`). This matters — Spring Cloud Config keys config by `spring.application.name`,
> so a mismatch would silently drop the port/route overrides and the gateway would start on `8080`
> with no routes. (The `settings.gradle` `rootProject.name = 'api-gateway'` is only the standalone
> Gradle project name and has no runtime effect.)

## Configuration

Source `src/main/resources/application.yml` holds only bootstrap config:

```yaml
spring:
  application:
    name: gateway-service
  config:
    import: "optional:configserver:"
  cloud:
    config:
      uri: http://localhost:8071
```

The routes are defined in `webstore-config/config/gateway-service.yml`. Each route strips its path
prefix via `RewritePath=/<prefix>/(?<path>.*), /$\{path}` before forwarding to a **direct URL**. The
target URLs are `${*_SERVICE_URL:...}` placeholders — the defaults suit host runs (`localhost` +
fixed port); Docker Compose overrides them with env vars pointing at Compose DNS names:

| External path   | Target property         | Host default            | Docker (env var value)          |
|-----------------|-------------------------|-------------------------|---------------------------------|
| `/inventory/**` | `INVENTORY_SERVICE_URL` | `http://localhost:8074` | `http://inventory-service:8074` |
| `/order/**`     | `ORDER_SERVICE_URL`     | `http://localhost:8077` | `http://order-service:8077`     |
| `/payment/**`   | `PAYMENT_SERVICE_URL`   | `http://localhost:8078` | `http://payment-service:8078`   |
| `/product/**`   | `PRODUCT_SERVICE_URL`   | `http://localhost:8073` | `http://product-service:8073`   |
| `/user/**`      | `USER_SERVICE_URL`      | `http://localhost:8075` | `http://user-service:8075`      |

To add or change a route, edit `gateway-service.yml` in the config repo and commit/push (the Config
Server reads from Git). Do not add routes to this service's source `application.yml`. If the new
target needs a different host per environment, follow the same `${*_SERVICE_URL:...}` placeholder
pattern and set the env var in `docker-compose.yml`.

## Build & Run

```bash
./gradlew :gateway-service:build
./gradlew :gateway-service:bootRun     # needs config-service up; business services only at request time
./gradlew :gateway-service:test        # context-load only
```

## Dependencies

- `spring-cloud-starter-gateway-server-webmvc` (servlet-stack API gateway; renamed from
  `spring-cloud-starter-gateway-mvc` in Spring Cloud 2025.x)
- `spring-cloud-starter-config` (fetches its own config from config-service)
- Spring Cloud BOM `2025.1.2`, Spring Boot `4.1.0` (Spring Framework 7), Java 25

> There is **no** Eureka client and **no** `lb://` load balancing — service discovery was removed
> from the system. Routes point at fixed URLs; in Kubernetes the platform's Services will do the
> load balancing.

## Startup Position

Needs only config-service (8071) to boot — routes are static, so no registry warm-up is required.
Requests to a backend that isn't up yet fail with a connection error until that service starts.
