package inventoryservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration: <b>every</b> inventory endpoint requires a valid JWT whose holder
 * has the {@code ADMIN} or {@code SERVICE} role. Unlike product-service, nothing here is public —
 * stock levels, prices, and reservations are not catalog data, and there is no anonymous use case.
 *
 * <p>Token validation is <b>local</b> — the JWKS is fetched from the issuer once and cached, so
 * auth-service is not called per request and is not in the hot path.
 *
 * <p><b>The realistic caller is a service, not a person.</b> order-service drives {@code /prices}
 * and {@code /reserve-stock} during order creation, under {@code client_credentials} — so
 * {@code SERVICE} is the role that carries normal traffic and {@code ADMIN} exists for warehouse
 * operations ({@code /increase-stock}, {@code /decrease-stock}, {@code DELETE /{sku}}) done by hand.
 * Both roles are unrestricted across all endpoints; split them per-path if warehouse adjustments
 * should stop being reachable by any service holding the shared client secret.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // Stateless bearer-token API: nothing is attached automatically by the browser, so
                // CSRF has no attack surface here. Leaving it enabled would reject every
                // POST/DELETE with 403 despite a perfectly valid token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure. The Compose healthcheck curls /actuator/health and
                        // Prometheus scrapes /actuator/prometheus every 15s — both would break.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        // API docs. springdoc is disabled entirely in PROD, so these 404 there.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Everything else — the whole of /v1/inventory/**, reads included. There is
                        // deliberately no public GET carve-out: available-count and prices expose
                        // commercial data, and no anonymous client has a reason to ask.
                        .anyRequest().hasAnyRole("ADMIN", "SERVICE"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Maps the {@code authorities} claim that auth-service adds onto the access token.
     *
     * <p>The default converter reads {@code scope} and prefixes each value with {@code SCOPE_},
     * which would yield {@code SCOPE_openid} / {@code SCOPE_profile} and never a role. Here the
     * claim name is overridden and the prefix cleared, so the values arrive verbatim —
     * {@code ROLE_ADMIN}, {@code ROLE_CUSTOMER}, {@code ROLE_SERVICE} — already carrying the prefix
     * auth-service applied, whether the role came from a user row or a client registration.
     *
     * <p><b>The empty prefix is load-bearing, and looks wrong at a glance.</b> It is tempting to
     * "fix" it to {@code ROLE_} since the claim holds only roles — but the claim values are already
     * prefixed, so that would produce {@code ROLE_ROLE_ADMIN} and 403 every call. Either both sides
     * carry the prefix (as here) or neither does; the two settings are a matched pair with
     * auth-service's {@code OAuth2TokenCustomizer}.
     *
     * <p><b>Why the plain Spring converter, where product-service has a custom one.</b>
     * product-service's {@code WebstoreJwtAuthenticationConverter} exists to lift the
     * {@code authUserId} claim into a typed {@code CustomAuthentication}. Nothing here is
     * user-scoped — inventory rows belong to SKUs, not people, and the normal caller is a machine
     * token with no user at all — so that class would be duplicated with no consumer. Add it if an
     * endpoint ever needs to record <em>who</em> adjusted stock; {@code inventory_change} would be
     * the natural place.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
