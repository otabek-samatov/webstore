package paymentservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration: <b>every</b> payment endpoint requires a valid JWT whose holder has
 * the {@code ADMIN} or {@code SERVICE} role. Nothing here is public — charges, refunds, and payment
 * lookups are financial records, and there is no anonymous use case.
 *
 * <p>Token validation is <b>local</b> — the JWKS is fetched from the issuer once and cached, so
 * auth-service is not called per request and is not in the hot path.
 *
 * <p><b>The realistic caller is a service, not a person.</b> order-service drives
 * {@code POST /v1/payments} from {@code ProcessPaymentStep} and {@code retryPayment}, under
 * {@code client_credentials} — so {@code SERVICE} carries the traffic that matters and
 * {@code ADMIN} exists for back-office work (issuing a refund, inspecting a payment). Both roles are
 * unrestricted across all endpoints.
 *
 * <p><b>{@code POST /refund} is worth a second look before this stays as-is.</b> Refunding is the one
 * operation here that moves money outward, and today any service holding the shared
 * {@code webstore-service-client} secret can invoke it — no webstore service actually needs to. If
 * this surface is ever tightened, that endpoint is the place to start: {@code hasRole("ADMIN")} alone
 * would match who is meant to use it.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // Stateless bearer-token API: nothing is attached automatically by the browser, so
                // CSRF has no attack surface here. Leaving it enabled would reject every POST with
                // 403 despite a perfectly valid token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Infrastructure. The Compose healthcheck curls /actuator/health and
                        // Prometheus scrapes /actuator/prometheus every 15s — both would break.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        // API docs. springdoc is disabled entirely in PROD, so these 404 there.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Everything else — the whole of /v1/payments/**, reads included. There is
                        // deliberately no public GET carve-out: a payment record names a user, an
                        // order, and an amount.
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
     * {@code authUserId} claim into a typed {@code CustomAuthentication}. Payments do carry a
     * {@code userId}, but it arrives in the request body from order-service — the caller is a machine
     * token with no user of its own, so a claim-derived id would be absent exactly when it is needed.
     * Introduce it only if a human-facing "my payments" endpoint is ever added, and note that the
     * claim is auth-service's id while {@code payment.user_id} is order-service's notion of the
     * customer; those are not the same number today.
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
