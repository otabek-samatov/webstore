package authservice.configs;

import authservice.entities.RoleType;
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
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
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
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                context.getClaims().claim(AUTHORITIES_CLAIM, clientAuthorities(context.getRegisteredClient()));
                return;
            }

            Set<String> roles = context.getPrincipal().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            context.getClaims().claim(AUTHORITIES_CLAIM, roles);
        };
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
