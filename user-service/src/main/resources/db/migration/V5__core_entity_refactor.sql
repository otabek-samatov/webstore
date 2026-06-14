-- Refactor: entities now extend CoreEntity (@MappedSuperclass) with:
--   * version column NOT NULL
--   * sequence allocationSize = 50 (DB sequences must increment by 50)
-- RoleType.USER was removed from the enum.

-- 1. version is now NOT NULL: backfill any existing NULLs, then enforce the constraint.
UPDATE security_role
SET version = 0
WHERE version IS NULL;
UPDATE user_profile
SET version = 0
WHERE version IS NULL;
UPDATE users
SET version = 0
WHERE version IS NULL;

ALTER TABLE security_role
    ALTER COLUMN version SET NOT NULL;
ALTER TABLE user_profile
    ALTER COLUMN version SET NOT NULL;
ALTER TABLE users
    ALTER COLUMN version SET NOT NULL;

-- 2. allocationSize 1 -> 50: increment sequences by 50 to match Hibernate's pooled optimizer,
--    and advance each past the current max id to avoid collisions.
ALTER SEQUENCE security_role_seq INCREMENT BY 50;
ALTER SEQUENCE user_profile_seq INCREMENT BY 50;
ALTER SEQUENCE user_seq INCREMENT BY 50;

SELECT setval('security_role_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM security_role), false);
SELECT setval('user_profile_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM user_profile), false);
SELECT setval('user_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM users), false);

-- 3. RoleType.USER removed: clean up any stale USER rows so they map to a valid enum value.
--    role_type is UNIQUE, so handle the case where a CUSTOMER row already exists:
--    repoint users from the USER role onto the existing CUSTOMER role, then drop the USER row.
UPDATE users u
SET security_role_id = c.id
FROM security_role usr,
     security_role c
WHERE u.security_role_id = usr.id
  AND usr.role_type = 'USER'
  AND c.role_type = 'CUSTOMER';

DELETE
FROM security_role
WHERE role_type = 'USER'
  AND EXISTS (SELECT 1 FROM security_role WHERE role_type = 'CUSTOMER');

-- If no CUSTOMER row exists, simply rename the USER row to CUSTOMER (no unique conflict).
UPDATE security_role
SET role_type = 'CUSTOMER'
WHERE role_type = 'USER';
