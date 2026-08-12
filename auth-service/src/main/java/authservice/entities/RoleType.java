package authservice.entities;

/**
 * The complete role vocabulary of the webstore.
 *
 * <p>Roles are the <b>only</b> authorization concept — there are no free-form permissions such as
 * {@code READ} / {@code WRITE}. A resource server writes its rules as {@code hasRole("ADMIN")}, and
 * the values here are the full set of names that may ever appear on the right-hand side.
 *
 * <p>Values are persisted by {@link Enum#name()} into {@code auth_schema.users_roles.role}, so
 * renaming a constant is a schema change and needs a Flyway migration. The same names (without the
 * {@code ROLE_} prefix, which is added on the way out — see
 * {@code authservice.security.SecurityUserDetails}) are what reach resource servers in the
 * {@code authorities} claim.
 */
public enum RoleType {
    ADMIN,
    CUSTOMER
}
