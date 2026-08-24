package orderservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration: every order endpoint requires a <b>valid token</b> — any role will
 * do. This is the one webstore service whose rule is {@code authenticated()} rather than
 * {@code hasAnyRole(...)}, because ordering is what a {@code CUSTOMER} is for: the other services
 * are either a public catalog (product) or back-office / machine surfaces (inventory, payment, user).
 *
 * <p>Token validation is <b>local</b> — the JWKS is fetched from the issuer once and cached, so
 * auth-service is not called per request and is not in the hot path.
 *
 * <p><b>{@code authenticated()} means any authenticated principal, and that is broader than it
 * sounds.</b> It admits {@code CUSTOMER}, {@code ADMIN}, and {@code SERVICE} alike — and it draws no
 * line <em>between</em> customers. Nothing here checks that the caller owns the order they are
 * asking about, so any authenticated user can read, mutate, or cancel <b>any</b> order by id:
 *
 * <ul>
 *   <li>{@code GET /v1/orders/{orderId}} — read someone else's order</li>
 *   <li>{@code GET /v1/orders/customer/{customerId}} — list someone else's entire order history</li>
 *   <li>{@code PUT /v1/orders/{orderId}/{status}} — cancel someone else's order</li>
 * </ul>
 *
 * <p>Closing that gap needs an <b>ownership</b> check, not another role: compare the caller's
 * identity against {@code Order.customerId}. The pieces are half-built —
 * product-service's {@code CustomAuthentication} already lifts an {@code authUserId} claim off the
 * token — but the claim carries {@code auth_schema.users.id} while {@code customerId} is
 * user-service's id, and nothing reconciles the two. That is the blocking decision, not the rule
 * itself. See the open architectural questions in {@code auth-service/CLAUDE.md}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // Stateless bearer-token API: nothing is attached automatically by the browser, so
                // CSRF has no attack surface here. Leaving it enabled would reject every
                // POST/PUT/DELETE with 403 despite a perfectly valid token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure. The Compose healthcheck curls /actuator/health and
                        // Prometheus scrapes /actuator/prometheus every 15s — both would break.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        // API docs. springdoc is disabled entirely in PROD, so these 404 there.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Everything else — all of /v1/orders/**. Any valid token, any role: a
                        // CUSTOMER places and tracks their own orders here. See the class javadoc
                        // for what this rule deliberately does NOT check.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Maps the {@code authorities} claim that auth-service adds onto the access token.
     *
     * <p><b>Still needed even though no rule here reads a role.</b> {@code authenticated()} only
     * asks whether a principal exists, so the authorities are unused today — but without this bean
     * the default converter would populate them from {@code scope} as {@code SCOPE_openid} /
     * {@code SCOPE_profile}, and the first {@code hasRole(...)} rule anyone adds (an admin-only
     * status override, say) would silently 403 for a genuine admin. Wiring it now costs nothing and
     * removes a trap later.
     *
     * <p>The claim name is overridden and the prefix cleared, so values arrive verbatim —
     * {@code ROLE_ADMIN}, {@code ROLE_CUSTOMER}, {@code ROLE_SERVICE} — already carrying the prefix
     * auth-service applied.
     *
     * <p><b>The empty prefix is load-bearing, and looks wrong at a glance.</b> It is tempting to
     * "fix" it to {@code ROLE_} since the claim holds only roles — but the claim values are already
     * prefixed, so that would produce {@code ROLE_ROLE_ADMIN} and 403 every rule that reads one.
     * Either both sides carry the prefix (as here) or neither does; the two settings are a matched
     * pair with auth-service's {@code OAuth2TokenCustomizer}.
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
