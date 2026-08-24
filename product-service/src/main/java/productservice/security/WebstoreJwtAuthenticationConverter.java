package productservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Turns a validated {@link Jwt} into a {@link CustomAuthentication} — the authorities Spring
 * Security needs for {@code hasRole(...)}, plus the {@code authUserId} claim auth-service mints.
 *
 * <p><b>Not named {@code JwtAuthenticationConverter}.</b> That is the name of the Spring class this
 * replaces ({@code org.springframework.security.oauth2.server.resource.authentication}), and two
 * types with one name in the same configuration is how an import gets silently swapped for the
 * wrong one.
 *
 * <p><b>The authorities mapping is delegated, not reimplemented.</b> {@link
 * JwtGrantedAuthoritiesConverter} handles an absent claim (empty collection → a clean 403 rather
 * than an NPE → 500), a claim that arrives as a delimited string instead of an array, and keeps the
 * claim name and prefix in one recognisable place. Hand-rolling it as
 * {@code jwt.getClaimAsStringList("authorities")} costs four lines and loses all three — and the
 * empty-claim case is one this system deliberately produces, since a machine client with no
 * {@code settings.client.role} is meant to fail closed.
 *
 * <p>Maps the {@code authorities} claim that auth-service adds onto the access token. The default
 * converter reads {@code scope} and prefixes each value with {@code SCOPE_}, which would yield
 * {@code SCOPE_openid} / {@code SCOPE_profile} and never a role. Here the claim name is overridden
 * and the prefix cleared, so the values arrive verbatim — {@code ROLE_ADMIN}, {@code ROLE_CUSTOMER},
 * {@code ROLE_SERVICE} — already carrying the prefix auth-service applied, whether the role came
 * from a user row or a client registration.
 *
 * <p><b>The empty prefix is load-bearing, and looks wrong at a glance.</b> It is tempting to "fix"
 * it to {@code ROLE_} since the claim holds only roles — but the claim values are already prefixed,
 * so that would produce {@code ROLE_ROLE_ADMIN} and 403 every admin write. Either both sides carry
 * the prefix (as here) or neither does; the two settings are a matched pair with auth-service's
 * {@code OAuth2TokenCustomizer}.
 */
@Component
public class WebstoreJwtAuthenticationConverter implements Converter<Jwt, CustomAuthentication> {

    /** Matched pair with auth-service's {@code AUTHORITIES_CLAIM}. Rename one, break the other. */
    private static final String AUTHORITIES_CLAIM = "authorities";

    /** Matched pair with auth-service's {@code AUTH_USER_ID_CLAIM}. Absent on machine tokens. */
    private static final String AUTH_USER_ID_CLAIM = "authUserId";

    private final JwtGrantedAuthoritiesConverter authoritiesConverter;

    public WebstoreJwtAuthenticationConverter() {
        this.authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        this.authoritiesConverter.setAuthoritiesClaimName(AUTHORITIES_CLAIM);
        this.authoritiesConverter.setAuthorityPrefix("");
    }

    @Override
    public CustomAuthentication convert(Jwt source) {
        Collection<GrantedAuthority> authorities =
                Objects.requireNonNullElseGet(authoritiesConverter.convert(source), List::of);

        return new CustomAuthentication(source, authorities, authUserId(source));
    }

    /**
     * The {@code authUserId} claim as a {@code Long}, or {@code null} when the token carries none.
     *
     * <p>Read as a string and parsed, rather than {@code source.getClaim(...)} into a {@code Long}
     * directly. {@code getClaim} casts unchecked, so a JSON integer small enough to deserialize as
     * an {@code Integer} would throw {@code ClassCastException} — a failure that would appear only
     * once ids grew past a certain size. auth-service mints the claim as a string for the same
     * reason; {@code getClaimAsString} converts either shape.
     *
     * <p>A non-numeric value throws rather than degrading to {@code null}. It would mean the issuer
     * changed the claim's format, and a token whose identity claim cannot be read should fail
     * loudly, not authenticate as an anonymous-but-authorized caller.
     */
    private static Long authUserId(Jwt source) {
        String claim = source.getClaimAsString(AUTH_USER_ID_CLAIM);
        return claim == null ? null : Long.valueOf(claim);
    }
}
