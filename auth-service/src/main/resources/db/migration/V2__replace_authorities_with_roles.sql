-- Authorities are replaced by a single role as the only authorization concept.
--
-- users_authorities held a flat mix of two different things: a role (ROLE_ADMIN) alongside
-- free-form permissions (READ, WRITE). Only the role survives, and a user holds exactly one, so it
-- becomes a column on users rather than a table of its own. A resource server now writes
-- hasRole("ADMIN") instead of hasAuthority("WRITE").
--
-- The column stores the bare enum name (ADMIN) — the ROLE_ prefix Spring Security expects is added
-- in SecurityUserDetails, not here.

-- Added nullable so the backfill below can populate it before the constraint is applied.
ALTER TABLE users
    ADD COLUMN role VARCHAR(50);

-- Collapse each user's authority rows down to one role.
--
-- ORDER BY picks ROLE_ADMIN over ROLE_CUSTOMER (alphabetical, and the two are the whole vocabulary),
-- so a user who somehow holds both keeps the privileges they had rather than silently losing them.
-- Permission rows (READ, WRITE) have no equivalent in the new model and are dropped with the table.
--
-- The IN list, rather than LIKE 'ROLE_%', is the guard that matters: the column is read back through
-- @Enumerated(STRING), so a value outside RoleType would migrate cleanly and then throw
-- IllegalArgumentException at login instead of failing here. Extend it only with names that exist in
-- authservice.entities.RoleType.
UPDATE users u
SET role = COALESCE(
        (SELECT SUBSTRING(a.authority FROM 6)
         FROM users_authorities a
         WHERE a.owner_id = u.id
           AND a.authority IN ('ROLE_ADMIN', 'ROLE_CUSTOMER')
         ORDER BY a.authority
         LIMIT 1),
    -- A user with no role row at all falls back to the least privileged role, never to ADMIN.
        'CUSTOMER');

ALTER TABLE users
    ALTER COLUMN role SET NOT NULL;

DROP TABLE users_authorities;
