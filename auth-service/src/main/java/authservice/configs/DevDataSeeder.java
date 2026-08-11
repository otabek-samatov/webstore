package authservice.configs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.UserDetailsManager;

/**
 * Seeds a single admin account so a fresh database is usable.
 *
 * <p>There is no registration endpoint yet, so this is currently the only way to get a row into
 * {@code auth_schema.users}. It goes through {@link UserDetailsManager#createUser}, which means the
 * password is encoded by the same {@code PasswordEncoder} the login path verifies against — no
 * hand-computed bcrypt hash, and no risk of the two drifting apart.
 *
 * <p>Active in the {@code dev} profile and when <em>no</em> profile is set (a plain host run), so it
 * can never run in UAT or PROD. Delete this class once a registration endpoint exists.
 */
@Configuration
@Profile({"default", "dev"})
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    @Bean
    CommandLineRunner seedAdminUser(
            UserDetailsManager userDetailsManager,
            @Value("${auth.dev.admin-username:admin}") String username,
            @Value("${auth.dev.admin-password:78}") String password) {

        return args -> {
            // userExists() is case-insensitive, so re-running against a populated DB is a no-op.
            if (userDetailsManager.userExists(username)) {
                log.info("Dev seed: user '{}' already exists, skipping", username);
                return;
            }

            userDetailsManager.createUser(User.withUsername(username)
                    .password(password)
                    .authorities("ROLE_ADMIN", "READ", "WRITE")
                    .build());

            log.warn("Dev seed: created user '{}' with a development password. "
                    + "This runs only in the dev/default profile.", username);
        };
    }
}
