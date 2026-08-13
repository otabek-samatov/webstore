package productservice.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import productservice.security.WebstoreJwtAuthenticationConverter;

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
                                           WebstoreJwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
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
}
