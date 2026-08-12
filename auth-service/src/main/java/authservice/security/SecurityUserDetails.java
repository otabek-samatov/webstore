package authservice.security;

import authservice.entities.AppUser;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUserDetails implements UserDetails {

    /**
     * Prefix Spring Security's {@code hasRole(...)} prepends before looking a role up among the
     * granted authorities. The database stores the bare name ({@code ADMIN}); the prefix is added
     * here, on the single boundary between the domain model and the framework.
     *
     * <p>Package-private so {@link SecurityUserDetailsManager} can strip it back off on the way in
     * without a second literal drifting out of sync with this one.
     */
    static final String ROLE_PREFIX = "ROLE_";

    private final AppUser user;

    public SecurityUserDetails(AppUser user) {
        this.user = user;
    }

    /**
     * The user's single role, exposed to Spring Security in its own vocabulary: {@code RoleType.ADMIN}
     * becomes the granted authority {@code ROLE_ADMIN}, which is exactly what {@code hasRole("ADMIN")}
     * tests for — here, and on every resource server that reads the {@code authorities} claim this
     * value is copied into.
     *
     * <p>A one-element collection because {@code UserDetails} demands one; the account model holds
     * exactly one role.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.getRole().name()));
    }

    @Override
    public @Nullable String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUserName();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getIsActive());
    }
}
