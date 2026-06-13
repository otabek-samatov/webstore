# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service Role

discovery-service is the **Netflix Eureka service registry** for the webstore system. Every other
service registers here on startup and discovers its peers by application name (enabling
`lb://<service>` load-balanced calls through the gateway and `RestClient`).

- **Entry point:** `DiscoveryServerApplication` — a `@SpringBootApplication` annotated
  `@EnableEurekaServer`.
- **Port:** `8070`
- **Eureka dashboard:** `http://localhost:8070`

## Naming (important)

There are **two** application names in play:

- **Bootstrap name (source `application.yml`):** `spring.application.name: discovery-service`. This
  is the key Spring Cloud Config uses to fetch `discovery-service.yml` from config-service.
- **Runtime name (overridden by `discovery-service.yml`):** `spring.application.name: discovery-server`.
  After config loads, the running app — and its Eureka registration identity — is **`discovery-server`**.

So the module is `discovery-service`, the config file is `discovery-service.yml`, and the running
service is `discovery-server`. The filename must match the **bootstrap** name (`discovery-service`)
for config resolution to work; the override to `discovery-server` happens afterward.

## Configuration

Source `src/main/resources/application.yml` holds only bootstrap config:

```yaml
spring:
  application:
    name: discovery-service
  config:
    import: "optional:configserver:"
  cloud:
    config:
      uri: http://localhost:8071
```

Everything else comes from `webstore-config/config/discovery-service.yml`, which makes this node the
Eureka **server** rather than a client:

- `server.port: 8070`
- `spring.application.name: discovery-server` (runtime override, see above)
- `eureka.client.registerWithEureka: false`, `eureka.client.fetchRegistry: false` — a standalone
  registry does not register with or fetch from itself
- `eureka.instance.hostname: localhost`
- `eureka.client.serviceUrl.defaultZone: http://localhost:8070/eureka/`
- `eureka.server.waitTimeInMsWhenSyncEmpty: 5` — short startup wait for a quicker local boot

## Gotchas

- **No Spring Boot Admin.** This is a plain Eureka registry — there is **no** Spring Boot Admin server
  dependency on the classpath and no `/admin` UI. (An earlier inert `spring.boot.admin.context-path`
  setting was removed from `discovery-service.yml`.) If you want the Admin dashboard, add
  `de.codecentric:spring-boot-admin-starter-server` and `@EnableAdminServer`, then re-add the config.
- **Depends on config-service at boot.** Because it imports its config from config-service, it cannot
  start until config-service (8071) is up.

## Build & Run

```bash
./gradlew :discovery-service:build
./gradlew :discovery-service:bootRun     # start AFTER config-service
./gradlew :discovery-service:test        # context-load only
```

## Dependencies

- `spring-cloud-starter-netflix-eureka-server`
- `spring-cloud-starter-config` (fetches its own config from config-service)
- Spring Cloud BOM `2024.0.1`, Spring Boot `3.4.4`, Java 21

## Startup Position

Start **second**, right after config-service and before the business/gateway services — they need the
registry available to register themselves and to resolve `lb://` targets.
