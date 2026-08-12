package authservice.security;

import authservice.entities.AppUser;
import authservice.repositories.AppUserRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * JPA-backed {@link UserDetailsManager} over {@link AppUser}.
 *
 * <p>Two deliberate deviations from Spring's {@code JdbcUserDetailsManager}:
 * <ul>
 *   <li><b>Passwords are encoded here</b>, not by the caller. {@code createUser} and
 *       {@code changePassword} take a <i>raw</i> password and run it through the
 *       {@link PasswordEncoder}, so a plaintext password can never reach the database.
 *       Do not pass an already-encoded value.</li>
 *   <li><b>{@code updateUser} does not touch the password</b> — a caller that loaded a
 *       {@code UserDetails} from the store and passed it back would otherwise re-encode the
 *       existing hash. Use {@link #changePassword(String, String)} instead.</li>
 * </ul>
 *
 * <p>Registered by component scanning. The {@link PasswordEncoder} it depends on is declared in
 * {@code authservice.configs.SecurityConfig}.
 */

@Component
public class SecurityUserDetailsManager implements UserDetailsManager {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public SecurityUserDetailsManager(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void createUser(UserDetails user) {
        String rawPassword = user.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }

        if (appUserRepository.existsAppUserByUserNameIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }

        AppUser appUser = new AppUser();
        appUser.setUserName(user.getUsername());
        appUser.setPassword(passwordEncoder.encode(rawPassword));
        appUser.setAuthorities(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        appUser.setIsActive(user.isEnabled());

        try {
            appUserRepository.saveAndFlush(appUser);
        } catch (DataIntegrityViolationException ex) {
            // The exists() check above is racy; the unique constraint on user_name is the real guard.
            throw new IllegalArgumentException("Username already exists: " + user.getUsername(), ex);
        }
    }

    @Override
    @Transactional
    public void updateUser(UserDetails user) {
        AppUser appUser = appUserRepository.findByUserNameIgnoreCase(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Username not found: " + user.getUsername()));

        appUser.setAuthorities(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        appUser.setIsActive(user.isEnabled());
        appUserRepository.save(appUser);
    }

    @Override
    @Transactional
    public void deleteUser(String username) {
        appUserRepository.findByUserNameIgnoreCase(username).ifPresent(appUserRepository::delete);
    }

    @Override
    @Transactional
    public void changePassword(@Nullable String oldPassword, @Nullable String newPassword) {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        if (currentUser == null) {
            throw new AccessDeniedException(
                    "Can't change password as no Authentication object found in context for current user.");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("New password must not be empty");
        }

        String username = currentUser.getName();
        AppUser appUser = appUserRepository.findByUserNameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found: " + username));

        if (oldPassword == null || !passwordEncoder.matches(oldPassword, appUser.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        appUser.setPassword(passwordEncoder.encode(newPassword));
        appUserRepository.save(appUser);

        // Drop the now-stale credentials from the context so the current session stays valid.
        UsernamePasswordAuthenticationToken newAuthentication = UsernamePasswordAuthenticationToken
                .authenticated(currentUser.getPrincipal(), null, currentUser.getAuthorities());
        newAuthentication.setDetails(currentUser.getDetails());
        SecurityContextHolder.getContext().setAuthentication(newAuthentication);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userExists(String username) {
        return appUserRepository.existsAppUserByUserNameIgnoreCase(username);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUserNameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found: " + username));

        // AppUser.authorities is an EAGER @ElementCollection, so SecurityUserDetails stays usable in the
        // filter chain after this transaction closes.
        return new SecurityUserDetails(appUser);
    }
}
