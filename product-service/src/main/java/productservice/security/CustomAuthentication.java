package productservice.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * The {@code Authentication} this service puts in the security context, extending the standard
 * {@link JwtAuthenticationToken} with the {@code authUserId} claim auth-service mints.
 *
 * <p><b>{@code getPrincipal()} still returns the {@link Jwt}, not this object.</b>
 * {@code JwtAuthenticationToken}'s constructor passes the token in as token, principal, and
 * credentials alike, and subclassing does not change that. So {@code @AuthenticationPrincipal Jwt}
 * keeps working, and reaching {@link #getAuthUserId()} means taking the {@code Authentication}
 * itself and casting:
 *
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<BookDto> create(@RequestBody BookDto dto, Authentication authentication) {
 *     Long authUserId = ((CustomAuthentication) authentication).getAuthUserId();
 * }
 * }</pre>
 *
 * <p>That cast is the whole cost of this class, and it is paid at every call site. It is worth
 * paying when several endpoints need the same derived value; for one call site,
 * {@code @AuthenticationPrincipal Jwt jwt} plus {@code jwt.getClaimAsString(...)} is less machinery.
 *
 * @see WebstoreJwtAuthenticationConverter
 */
public class CustomAuthentication extends JwtAuthenticationToken {

    private final Long authUserId;

    /**
     * @param authUserId the {@code authUserId} claim, or {@code null} — see {@link #getAuthUserId()}
     */
    public CustomAuthentication(Jwt jwt,
                                Collection<? extends GrantedAuthority> authorities,
                                Long authUserId) {
        super(jwt, authorities);
        this.authUserId = authUserId;
    }

    /**
     * The authenticated user's {@code auth_schema.users.id}, or {@code null} when the token names no
     * user.
     *
     * <p><b>Null is a normal case here, not a failure.</b> A {@code client_credentials} token —
     * which this service accepts on every write, since {@code ROLE_SERVICE} satisfies the catch-all
     * rule — has no user behind it and therefore no {@code authUserId} claim. Code that needs a
     * human must handle the machine caller explicitly rather than assume one. Unboxing this to a
     * {@code long} without a null check NPEs on service-to-service traffic only.
     *
     * <p><b>This is auth-service's id, not user-service's.</b> The two services own separate
     * {@code users} tables whose ids are different numbers for the same person. Do not use this
     * value to look anything up in {@code user_schema} until that is reconciled — see the open
     * architectural questions in {@code auth-service/CLAUDE.md}.
     */
    public Long getAuthUserId() {
        return authUserId;
    }
}
