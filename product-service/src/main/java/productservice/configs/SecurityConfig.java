package productservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration: the catalog is readable by anyone, but modifying it requires a
 * valid JWT whose holder has the {@code ADMIN} or {@code SERVICE} role.
 *
 * <p>Token validation is <b>local</b> — the JWKS is fetched from the issuer once and cached, so
 * auth-service is not called per request and is not in the hot path.
 *
 * <p>Rules are written default-deny: reads are listed explicitly and everything else falls through
 * to {@code hasAnyRole("ADMIN", "SERVICE")}. A controller added later is therefore protected until
 * someone deliberately opens it, rather than being public by accident.
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
                        // Catalog reads are public: browsing books needs no account.
                        .requestMatchers(HttpMethod.GET, "/v1/books/**").permitAll()
                        // Everything else — POST / PUT / DELETE on every controller — needs a token
                        // whose holder is an ADMIN (a human) or SERVICE (another webstore service
                        // authenticating under client_credentials). SERVICE is unrestricted here:
                        // internal callers may do anything a catalog admin can.
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
     * prefixed, so that would produce {@code ROLE_ROLE_ADMIN} and 403 every admin write. Either both
     * sides carry the prefix (as here) or neither does; the two settings are a matched pair with
     * auth-service's {@code OAuth2TokenCustomizer}.
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
