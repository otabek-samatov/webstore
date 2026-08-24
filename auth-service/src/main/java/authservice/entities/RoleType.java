package authservice.entities;

import java.util.Arrays;

/**
 * The complete role vocabulary of the webstore.
 *
 * <p>Roles are the <b>only</b> authorization concept — there are no free-form permissions such as
 * {@code READ} / {@code WRITE}. A resource server writes its rules as {@code hasRole("ADMIN")}, and
 * the values here are the full set of names that may ever appear on the right-hand side.
 *
 * <p>Values are persisted by {@link Enum#name()} into {@code auth_schema.users.role}, so renaming a
 * constant is a schema change and needs a Flyway migration.
 *
 * <p>This enum also owns the translation between the domain spelling ({@code ADMIN}) and Spring
 * Security's ({@code ROLE_ADMIN}) — see {@link #authority()} and {@link #fromAuthority(String)}.
 * Keeping both directions here means the {@code ROLE_} literal exists exactly once in the codebase;
 * a second copy that drifts produces {@code ROLE_ROLE_ADMIN} or a silently unmatched rule.
 */
public enum RoleType {

    /** Human administrator: the only role permitted to modify the catalog. */
    ADMIN,

    /** Ordinary shopper. The default for any account with no role of its own. */
    CUSTOMER,

    /**
     * Machine clients calling under {@code client_credentials} — service-to-service traffic such as
     * order-service reserving stock.
     *
     * <p>Never assigned to a user account. It is granted by client registration, not by a row in
     * {@code users}, and {@code SecurityUserDetailsManager} rejects any attempt to store it against
     * a person.
     */
    SERVICE;

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * This role in Spring Security's spelling: {@code ADMIN} → {@code ROLE_ADMIN}.
     *
     * <p>That is the exact string {@code hasRole("ADMIN")} tests for, the value handed to
     * {@code SimpleGrantedAuthority}, and the value that travels in the token's {@code authorities}
     * claim — which is why resource servers configure an <b>empty</b> authority prefix.
     */
    public String authority() {
        return ROLE_PREFIX + name();
    }

    /**
     * Parses either spelling — {@code ROLE_ADMIN} or the bare {@code ADMIN} — into a constant.
     *
     * <p>Both are accepted because Spring's own builder produces both:
     * {@code User.withUsername(…).roles("ADMIN")} yields the prefixed form,
     * {@code .authorities("ADMIN")} the bare one.
     *
     * @throws IllegalArgumentException naming the valid values, if it is neither
     */
    public static RoleType fromAuthority(String authority) {
        String name = authority.startsWith(ROLE_PREFIX)
                ? authority.substring(ROLE_PREFIX.length())
                : authority;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role: " + name + ". Valid roles are "
                    + Arrays.toString(values()), ex);
        }
    }
}
