# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

auth-service is the **OAuth 2.1 / OpenID Connect authorization server** for the webstore system.
It issues tokens and owns a credential store (`auth_schema.users`); it does not serve business data.

- **Port:** `8076` (`${SERVICE_PORT:8076}` in `webstore-config/config/auth-service.yml`)
- **Schema:** `auth_schema`
- **Gateway route:** `/auth/**` (prefix stripped via `RewritePath`)
- **Base package:** `authservice`

> ⚠️ **This service is not yet functional.** It compiles and the schema migrates, but there are no
> controllers, no way to create the first user, no tests, and no service consumes the tokens it
> issues. See [Current State](#current-state) before assuming any part of it works end to end.

## Build and Run

```bash
./gradlew :auth-service:build
./gradlew :auth-service:bootRun
./gradlew :auth-service:compileJava     # fastest feedback while iterating on SecurityConfig
```

Host runs need three environment variables — all three fail startup if missing, by design:

```bash
export DB_USERNAME=$(cat secrets/postgres_user.txt)
export DB_PASSWORD=$(cat secrets/postgres_password.txt)
export AUTH_CLIENT_SECRET=$(cat secrets/auth_client_secret.txt)
```

> `auth-service/` also contains its own `settings.gradle`, `gradlew`, `gradlew.bat`,
> `build.gradle.bak`, and `.iml` left over from scaffolding. The module is built from the **root**
> `settings.gradle`; the nested wrapper is dead weight and only confuses IDE import.

## Spring Security 7 — read this before touching SecurityConfig

**Spring Authorization Server was merged into Spring Security in 7.0.** It is no longer a separate
project with its own release train. Two consequences that will waste your time if you don't know them:

**1. The config classes moved; the domain classes did not.**

| Class | Package |
|---|---|
| `OAuth2AuthorizationServerConfiguration` | `org.springframework.security.config.annotation.web.configuration` |
| `OAuth2AuthorizationServerConfigurer` | `org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization` |
| `RegisteredClient`, `RegisteredClientRepository`, `InMemoryRegisteredClientRepository` | `org.springframework.security.oauth2.server.authorization.client` |
| `ClientSettings`, `AuthorizationServerSettings` | `org.springframework.security.oauth2.server.authorization.settings` |

Any tutorial or answer written before 7.0 will use
`org.springframework.security.oauth2.server.authorization.config.annotation.web.*` for the first two.
Those paths no longer exist.

**2. Being in the BOM is not being on the classpath.** `spring-boot-starter-security` resolves only
`spring-security-config`, `-core`, `-crypto`, `-web`. The authorization server is a separate artifact:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-oauth2-authorization-server'
```

**3. `applyDefaultSecurity(HttpSecurity)` no longer exists.** In 7.0 the only static method on
`OAuth2AuthorizationServerConfiguration` is `jwtDecoder(JWKSource)`. Use the DSL instead:

```java
http.oauth2AuthorizationServer(authorizationServer -> {
    http.securityMatcher(authorizationServer.getEndpointsMatcher());
    authorizationServer.oidc(Customizer.withDefaults());
});
```

**4. There is no password grant.** OAuth 2.1 removed resource-owner-password-credentials, and the
authorization server does not implement it. You cannot POST username+password to `/oauth2/token`.
User login must go through authorization_code. Since there is no UI, `formLogin` serves Spring's
**generated** login page and Postman's OAuth 2.0 helper drives it in a browser window.

## Architecture

```
authservice/
├── AuthServiceApplication.java
├── entities/           AppUser, CoreEntity, Authority (unused)
├── repositories/       AppUserRepository
└── security/           SecurityConfig, AuthUserDetailsManager, SecurityUser
```

There are deliberately **no** `controllers/`, `managers/`, `dto/`, or `mappers/` packages yet — see
[Current State](#current-state).

### Domain model

```
users (auth_schema)
└── users_authorities   (@ElementCollection, EAGER, owner_id → users.id)
```

`AppUser` extends `CoreEntity` (`id` + `@Version version`), same base-class pattern as every other
service. Sequence `user_seq`, `allocationSize = 50`.

| Column | Notes |
|---|---|
| `user_name` | `updatable = false`; `setUserName` throws `IllegalStateException` if reassigned |
| `password` | always a `{bcrypt}` hash — see the encoding convention below |
| `created_at` | `@CreationTimestamp` |
| `is_active` | `NOT NULL DEFAULT TRUE`; drives `SecurityUser.isEnabled()` |

### Security classes

- **`SecurityUser`** — `UserDetails` wrapper around `AppUser`. `isEnabled()` returns
  `Boolean.TRUE.equals(user.getIsActive())`, so a null reads as **disabled** (fail closed).
- **`AuthUserDetailsManager`** — `@Component`, implements `UserDetailsManager`. All logic lives here.
- **`SecurityConfig`** — two filter chains, client registrations, `PasswordEncoder`, `JWKSource`.

## Conventions and traps

### `userName` vs `username`

The entity property is **`userName`**. `UserDetails.getUsername()` is **`username`**. These do not
match, and Spring Data resolves derived queries against the *entity*:

- `findByUserNameIgnoreCase` ✅ resolves to `userName`
- `findByUsername` ❌ `PropertyReferenceException: No property 'username' found for type 'AppUser'`

That failure happens at **context startup**, not at call time. Repository code reads a little oddly
as a result — `findByUserNameIgnoreCase(user.getUsername())` — and that is correct.

### Usernames are unique case-insensitively

`Admin` and `admin` are the same account. Enforced by a unique **expression index**, because Postgres
cannot express this as a table constraint:

```sql
CREATE UNIQUE INDEX uc_users_user_name_ci ON users (UPPER(user_name));
```

**`UPPER` is load-bearing.** Spring Data's `IgnoreCase` keyword renders `upper(...)`. A `LOWER(...)`
index would still be correct but silently unusable by the query — a sequential scan on every login.
If you change one side, change the other. Every repository method and every call site in
`AuthUserDetailsManager` must stay `…IgnoreCase`; an exact-match lookup would let a duplicate past the
`exists` check and turn a clean `IllegalArgumentException` into a `DataIntegrityViolationException`.

The stored value keeps its original casing — only *matching* is case-insensitive.

### Passwords are encoded inside the manager

This is a **deliberate deviation** from Spring's `JdbcUserDetailsManager`, where the caller supplies
an already-encoded password:

- `createUser` and `changePassword` take a **raw** password and run it through the `PasswordEncoder`.
  Never pass an already-encoded value — it will be double-encoded.
- `updateUser` deliberately **does not touch the password**. A caller round-tripping a `UserDetails`
  loaded from the store would otherwise re-encode the existing hash. Use `changePassword`.

The same rule applies to client secrets in `SecurityConfig` — `passwordEncoder.encode(...)`, never a
literal `{noop}` value.

### `@ElementCollection` is EAGER on purpose

`SecurityUser` is read by the filter chain long after `loadUserByUsername`'s transaction closes.
LAZY would throw `LazyInitializationException`. If a future query returns many `AppUser` rows, keep
EAGER and add `@BatchSize` rather than reverting.

## SecurityConfig layout

Two chains, and the order matters:

| Bean | Order | Scope |
|---|---|---|
| `authorizationServerFilterChain` | 1 | `securityMatcher(getEndpointsMatcher())` — protocol endpoints only |
| `defaultFilterChain` | 2 | everything else; `formLogin` + `httpBasic`, actuator health/prometheus `permitAll` |

Dropping the `securityMatcher` makes chain 1 match every request; dropping `authorizeHttpRequests`
removes the `AuthorizationFilter` entirely and leaves the service **fully open** — it fails open, not
loudly. `httpBasic` exists so Postman can exercise `AuthUserDetailsManager` without the full
authorization-code flow.

### Registered clients

| Client | Grant | Authentication |
|---|---|---|
| `postman-client` | authorization_code + refresh_token, PKCE required | public (`NONE`), redirect `https://oauth.pstmn.io/v1/callback` |
| `webstore-service-client` | client_credentials | secret from `auth_client_secret`, bcrypt-encoded at startup |

Only the authorization_code flow authenticates a real user, so it is the only one that exercises
`AuthUserDetailsManager`.

### Secrets

`auth_client_secret` follows the same path as the DB credentials: `secrets/auth_client_secret.txt`
(gitignored) → Docker secret → `/run/secrets/auth_client_secret` → property via the
`optional:configtree:/run/secrets/` import. Host runs use `AUTH_CLIENT_SECRET`. There is **no inline
default** — a missing secret must fail startup rather than fall back to a known value.

## Database

Flyway migrations in `src/main/resources/db/migration/`. `V1__init_tables.sql` creates `user_seq`,
`users`, `users_authorities`, and the case-insensitive unique index.

`ddl-auto: validate` (inherited from the config repo) means entity/schema drift is fatal at startup.
Hibernate validates **tables, columns, and types** — not constraints, indexes, or nullability. That
is why `is_active NOT NULL` and the expression index don't conflict with the mapping.

> `AppUser` still declares `unique = true` and `@Index(name = "idx_user_user_name", …)`. Neither
> exists in the schema any more — both were replaced by `uc_users_user_name_ci`. Harmless (the
> validator ignores them) but misleading; worth deleting.

<a id="current-state"></a>## Current State

**Works:** schema migrates; `SecurityConfig` builds two correctly-scoped chains; password and client
secret encoding; case-insensitive username matching; disabled-user rejection.

**Does not work / not done:**

| Gap | Consequence |
|---|---|
| No controllers | `createUser` / `updateUser` / `deleteUser` / `changePassword` are unreachable from outside the JVM |
| No user rows, nothing seeds | No way to obtain the first user, so no way to log in |
| No tests | Only a 13-line `contextLoads` that fails without config-server + DB |
| Issuer not pinned | Discovery advertises the request host, so through the gateway it publishes unreachable container URLs. Needs an `AuthorizationServerSettings` bean with the external issuer |
| `/userinfo` returns 401 | `.oidc()` enables the endpoint but chain 1 has no `.oauth2ResourceServer(...jwt())` to authenticate bearer tokens |
| RSA key regenerated per boot | Restarts invalidate all tokens; two instances sign with different keys. DEV-only — needs a keystore |
| In-memory `RegisteredClientRepository` | Clients vanish on restart. Move to `JdbcRegisteredClientRepository` |
| CSRF enabled, no API carve-out | Fine today (`formLogin` carries the token); will 403 Postman `POST`s once controllers exist |
| No service validates the tokens | The other six services are not resource servers, so nothing consumes what this issues |

### Open architectural questions

1. **Duplicate user store.** user-service owns `user_schema.users` + `security_role`; auth-service
   owns `auth_schema.users` + `users_authorities`. Two systems of record for the same people, and
   nothing reconciles them. This should be settled before more is built on either side.
2. **Do the other six services become resource servers?** If yes, each needs
   `spring-boot-starter-oauth2-resource-server` + `issuer-uri`, the gateway must forward tokens, and
   every existing endpoint needs an authorization rule.
3. **`Authority` enum (`READ` / `WRITE`) is unused** — authorities are free-form strings.

## Deployment

- **Compose:** service block between user-service and order-service. It is the only business service
  needing a **third** secret, so it cannot reuse the `*db-secrets` YAML anchor (merge keys cannot
  extend a sequence) and lists all three explicitly.
- **`.env`:** `AUTH_SERVICE_PORT=8076`.
- **Gateway:** `AUTH_SERVICE_URL` in the `x-service-urls` anchor; `/auth/**` route in
  `gateway-service.yml`. Config-repo changes only take effect once **committed and pushed**.
- **Prometheus:** ⚠️ no scrape job in `monitoring/prometheus/prometheus.yml` yet.
