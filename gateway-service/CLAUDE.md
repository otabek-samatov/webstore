# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service Role

gateway-service is the **API Gateway** — the single external entry point that routes incoming
requests to the backend services discovered through Eureka. It is built on **Spring Cloud Gateway
Server Web MVC** (the servlet-stack gateway, not the reactive WebFlux one).

> **Spring Cloud 2025.x rename:** the old `spring-cloud-starter-gateway-mvc` artifact was deprecated in
> 2025.0 and is gone in 2025.1 — this service now depends on **`spring-cloud-starter-gateway-server-webmvc`**.
> The route DSL/config is unchanged; only the starter coordinate moved. (The HTTP-client config that was
> nested under the old `mvc` namespace is now top-level `gateway` config, but this service doesn't set it.)

- **Entry point:** `ApiGatewayApplication` — a plain `@SpringBootApplication` (Eureka client
  auto-configures from the classpath; no explicit `@EnableDiscoveryClient` needed).
- **Spring `application.name`:** `gateway-service` (matches its config file `gateway-service.yml`, so
  Spring Cloud Config serves it the port + routes correctly)
- **Port:** `8072` (from `gateway-service.yml`)
- Registers with Eureka and resolves route targets via `lb://<service>` load balancing.

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
prefix via `RewritePath=/<prefix>/(?<path>.*), /$\{path}` before forwarding to the Eureka-named
service:

| External path   | Routed to (`lb://`)      |
|-----------------|--------------------------|
| `/inventory/**` | `lb://inventory-service` |
| `/order/**`     | `lb://order-service`     |
| `/payment/**`   | `lb://payment-service`   |
| `/product/**`   | `lb://product-service`   |
| `/user/**`      | `lb://user-service`      |

To add or change a route, edit `gateway-service.yml` in the config repo and commit/push (the Config
Server reads from Git). Do not add routes to this service's source `application.yml`.

## Build & Run

```bash
./gradlew :gateway-service:build
./gradlew :gateway-service:bootRun     # start LAST, after config + discovery + business services
./gradlew :gateway-service:test        # context-load only
```

## Dependencies

- `spring-cloud-starter-gateway-server-webmvc` (servlet-stack API gateway; renamed from
  `spring-cloud-starter-gateway-mvc` in Spring Cloud 2025.x)
- `spring-cloud-starter-netflix-eureka-client` (registers + discovers `lb://` targets)
- `spring-cloud-starter-config` (fetches its own config from config-service)
- Spring Cloud BOM `2025.1.2`, Spring Boot `4.1.0` (Spring Framework 7), Java 21

## Startup Position

Start **last** — after config-service (8071), discovery-service (8070), and the downstream business
services. The gateway resolves `lb://` targets from Eureka at request time; targets that aren't
registered yet return errors until they come up.
