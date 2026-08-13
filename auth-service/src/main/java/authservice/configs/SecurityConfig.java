package authservice.configs;

import authservice.entities.AppUser;
import authservice.entities.RoleType;
import authservice.repositories.AppUserRepository;
import authservice.security.SecurityUserDetails;
import authservice.security.SecurityUserDetailsManager;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    /**
     * Name of the claim carrying the principal's role. Read verbatim by every resource server's
     * {@code JwtGrantedAuthoritiesConverter} — see the customizer below before renaming it.
     */
    private static final String AUTHORITIES_CLAIM = "authorities";

    /**
     * Name of the claim carrying the principal's {@code auth_schema.users.id}, read by
     * product-service's {@code WebstoreJwtAuthenticationConverter} into {@code CustomAuthentication}.
     *
     * <p><b>Named after its store, deliberately.</b> user-service owns a second {@code users} table
     * whose ids are different numbers for the same people, and nothing reconciles the two — see the
     * open architectural questions in this service's {@code CLAUDE.md}. A claim called
     * {@code userId} would assert a canonical platform-wide id that does not exist yet; this name
     * states which store the value came from, so it never has to be un-baked out of tokens already
     * in circulation. Rename it once that question is settled, not before.
     *
     * <p><b>Minted as a string, not a number.</b> A JSON integer arrives as {@code Integer} or
     * {@code Long} depending on magnitude, and {@code Jwt.getClaim} casts unchecked — so a numeric
     * claim would work until the sequence crossed {@code Integer.MAX_VALUE} and then throw
     * {@code ClassCastException}. {@code getClaimAsString} converts rather than casts.
     *
     * <p>Set only under user-bearing grants; see the customizer below.
     */
    private static final String AUTH_USER_ID_CLAIM = "authUserId";

    /**
     * Key under which a machine client's {@link RoleType} is stored in its {@link ClientSettings}.
     *
     * <p>Held as client data rather than a branch in the customizer so that registering another
     * service client is a registration change only — and so it survives the move to
     * {@code JdbcRegisteredClientRepository}, where clients become rows instead of code.
     */
    private static final String CLIENT_ROLE_SETTING = "settings.client.role";

    /**
     * Protocol endpoints only ({@code /oauth2/**}, {@code /.well-known/**}, {@code /userinfo}).
     * The security matcher keeps this chain off every other path so the chain below can own them.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // Unauthenticated browser hits on the authorization endpoint redirect to the
                // generated login page — this is what Postman's OAuth 2.0 helper follows.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    /**
     * Everything else. {@code formLogin} serves the generated login page the chain above redirects
     * to; {@code httpBasic} exists so Postman can authenticate against
     * {@link SecurityUserDetailsManager} directly without running the full authorization-code flow.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        // Infrastructure. The Compose healthcheck curls /actuator/health, and
                        // Prometheus is set up to scrape /actuator/prometheus — both would break.
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        // API docs. Public only in DEV/UAT: the config repo's application-prod.yml
                        // disables springdoc outright, so these 404 in PROD rather than relying on
                        // this rule. The spec is empty today — this service has no controllers yet.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * @param serviceClientSecret raw secret, encoded below with the {@link PasswordEncoder} bean so
     *                            the stored value is a {@code {bcrypt}} hash, never plaintext.
     *                            Resolved exactly like the DB credentials — from
     *                            {@code /run/secrets/auth_client_secret} via the configtree import
     *                            in containers, or the {@code AUTH_CLIENT_SECRET} env var on host
     *                            runs. No default on purpose: a missing secret must fail startup
     *                            rather than silently fall back to a known value.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${auth_client_secret}") String serviceClientSecret) {
        // Public client + PKCE, driven by Postman's OAuth 2.0 helper. This is the only flow that
        // authenticates a real user, so it is what exercises SecurityUserDetailsManager.
        RegisteredClient postmanClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://oauth.pstmn.io/v1/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .build();

        // Machine-to-machine: one POST to /oauth2/token, no user involved. This is the grant the
        // other webstore services use to call each other — order-service reserving stock, for
        // instance. The SERVICE role below is what lets such a token satisfy a hasRole(...) rule;
        // without it the token carries no role at all and every protected endpoint 403s.
        RegisteredClient serviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("webstore-service-client")
                .clientSecret(passwordEncoder.encode(serviceClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("webstore.read")
                .scope("webstore.write")
                .clientSettings(ClientSettings.builder()
                        .setting(CLIENT_ROLE_SETTING, RoleType.SERVICE.name())
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(postmanClient, serviceClient);
    }

    /**
     * Copies the authenticated user's roles into an {@code authorities} claim on the access token,
     * in Spring Security's own spelling — {@code RoleType.ADMIN} reaches the wire as
     * {@code ROLE_ADMIN}.
     *
     * <p>Without this a token carries only {@code scope} (e.g. {@code openid}, {@code profile}),
     * which describes what the <em>client</em> was granted — not who the <em>user</em> is. A
     * resource server would see {@code SCOPE_openid} and nothing else, so any rule like
     * {@code hasRole("ADMIN")} would always 403 even for a genuine admin.
     *
     * <p>The claim keeps the name {@code authorities} even though it carries only roles: its
     * values are still literal {@code GrantedAuthority} strings, prefix included, which is exactly
     * what a resource server's {@code JwtGrantedAuthoritiesConverter} expects to read verbatim.
     * Renaming it to {@code roles} would invite stripping the prefix too, and a claim of bare
     * {@code ADMIN} silently fails every {@code hasRole("ADMIN")} check.
     *
     * <p>Also adds an {@link #AUTH_USER_ID_CLAIM} carrying {@code users.id} — user-bearing grants
     * only, since {@code client_credentials} has no user behind it.
     *
     * <p>Only access tokens are customized; the id_token is an identity document and doesn't need
     * them.
     *
     * <p><b>Two sources, because the principal differs by grant.</b> Under {@code authorization_code}
     * the principal is the end user and the role comes from {@code auth_schema.users}. Under
     * {@code client_credentials} the principal is the client itself, whose authorities are always
     * empty — so the role is read from its registration instead. Without that branch a
     * service-to-service token would carry no role and 403 on every protected endpoint.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(AppUserRepository users) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            Set<String> roles;

            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                roles = clientAuthorities(context.getRegisteredClient());
            } else {
                roles = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                // Inside the user branch on purpose: there is no user under client_credentials, so a
                // claim added at the outer level would put "authUserId": null on every machine token
                // and NPE consumers that unbox it — on service-to-service traffic only.
                authUserId(users, context.getPrincipal())
                        .ifPresent(id -> context.getClaims().claim(AUTH_USER_ID_CLAIM, id));
            }

            context.getClaims().claim(AUTHORITIES_CLAIM, roles);
        };
    }

    /**
     * The authenticated user's {@code users.id}, as a string, or empty if the row cannot be found.
     *
     * <p><b>Read from the repository rather than off the principal.</b> Casting
     * {@code context.getPrincipal().getPrincipal()} to {@link SecurityUserDetails} would reach the
     * same row without a query, and works today only because
     * {@code InMemoryOAuth2AuthorizationService} holds the principal as a live object. Under the
     * planned {@code JdbcOAuth2AuthorizationService} the authorization is serialized to JSON, and
     * {@code SecurityUserDetails} has no Jackson mixin — the cast would stop matching and the claim
     * would silently vanish from every token. {@code getName()} survives that serialization, so
     * looking the row up by name does too.
     *
     * <p>The query runs once per token issued, not per request, and the lookup is index-backed
     * (<code>uc_users_user_name_ci</code>).
     *
     * <p>Empty is treated as "no claim" rather than an error: the account was deleted between login
     * and token issue. A token with no id is one a resource server rejects for lack of a user, which
     * is the right outcome — better than a token asserting an id that no longer resolves.
     */
    private static Optional<String> authUserId(AppUserRepository users, Authentication principal) {
        return users.findByUserNameIgnoreCase(principal.getName())
                .map(AppUser::getId)
                .map(String::valueOf);
    }

    /**
     * The role a machine client holds, taken from its {@link ClientSettings}.
     *
     * <p>A client with no {@link #CLIENT_ROLE_SETTING} gets an empty claim rather than a default —
     * granting a role to every registered client by accident is exactly the failure this is meant to
     * prevent. {@code postman-client} has none, so even if it were ever given
     * {@code client_credentials} it could not reach a service endpoint.
     */
    private static Set<String> clientAuthorities(RegisteredClient registeredClient) {
        String role = registeredClient.getClientSettings().getSetting(CLIENT_ROLE_SETTING);
        return role == null ? Set.of() : Set.of(RoleType.valueOf(role).authority());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

}
