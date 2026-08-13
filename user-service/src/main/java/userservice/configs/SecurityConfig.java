package userservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration: <b>every</b> user endpoint requires a valid JWT whose holder has the
 * {@code ADMIN} or {@code SERVICE} role. Nothing here is public — accounts, profiles, addresses, and
 * role assignments are personal data.
 *
 * <p>Token validation is <b>local</b> — the JWKS is fetched from the issuer once and cached, so
 * auth-service is not called per request and is not in the hot path.
 *
 * <p><b>No webstore service calls this one today</b>, so unlike inventory-service and
 * payment-service these rules break no existing traffic. {@code SERVICE} is granted anyway, for
 * symmetry and because a future caller (an order enriching a shipping address, say) would need it.
 *
 * <p><b>Two consequences of the blanket rule, both deliberate and both worth revisiting.</b>
 *
 * <p>1. <b>A {@code CUSTOMER} cannot read their own profile.</b> {@code GET /v1/users/profile/{id}}
 * requires {@code ADMIN} or {@code SERVICE}, so self-service is impossible through this API. When a
 * "my profile" path is wanted, the rule to add is ownership-based rather than another role — the
 * caller's identity would have to be matched against the requested id, which is what the
 * {@code authUserId} claim exists for in product-service. Note the id mismatch first: that claim
 * carries {@code auth_schema.users.id}, not {@code user_schema.users.id}.
 *
 * <p>2. <b>{@code POST /v1/users/user} is admin-only, so nobody can self-register.</b> That is
 * consistent with the platform today — auth-service has no registration endpoint either, and its
 * only account arrives via the dev-profile seeder — but the two will have to be solved together, not
 * separately. See the duplicate-user-store question in {@code auth-service/CLAUDE.md}.
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
                        // Everything else — all three controllers, reads included. There is
                        // deliberately no public carve-out: /v1/users/user, /v1/users/profile and
                        // /v1/users/role all expose or mutate personal data.
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
     * <p><b>A note specific to this service.</b> The roles a token carries come from
     * {@code auth_schema.users.role}; the roles this service stores in {@code security_role} are a
     * <em>separate</em> record of the same idea. Changing a user's role through
     * {@code PUT /v1/users/role/{userID}} therefore does <b>not</b> change what any future token
     * says. Until the two stores are reconciled, treat that endpoint as editing a local copy.
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
