package authservice.security;

import authservice.entities.AppUser;
import authservice.entities.RoleType;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUserDetails implements UserDetails {

    private final AppUser user;

    public SecurityUserDetails(AppUser user) {
        this.user = user;
    }

    /**
     * The user's single role, in Spring Security's vocabulary — {@code RoleType.ADMIN} becomes
     * {@code ROLE_ADMIN}, which is exactly what {@code hasRole("ADMIN")} tests for, here and on every
     * resource server that reads the {@code authorities} claim this value is copied into.
     *
     * <p>A one-element collection because {@code UserDetails} demands one; the account model holds
     * exactly one role. The prefixing itself lives in {@link RoleType#authority()}.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(user.getRole().authority()));
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
