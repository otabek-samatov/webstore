CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE users
(
    id         BIGINT                      NOT NULL,
    version    INTEGER                     NOT NULL,
    user_name  VARCHAR(255)                NOT NULL,
    password   VARCHAR(255)                NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active  BOOLEAN                     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- @ElementCollection on AppUser.authorities. Set<String> semantics are enforced
-- by the composite primary key: a user cannot hold the same authority twice.
CREATE TABLE users_authorities
(
    owner_id  BIGINT       NOT NULL,
    authority VARCHAR(255) NOT NULL,
    CONSTRAINT pk_users_authorities PRIMARY KEY (owner_id, authority)
);

-- Usernames are unique case-insensitively: "Admin" and "admin" are the same
-- account. Postgres cannot express this as a table constraint, so it is a
-- unique index over an expression. UPPER (not LOWER) is deliberate — Spring
-- Data's IgnoreCase keyword renders upper(...), and the index is only usable
-- by the lookup if both sides agree.
CREATE UNIQUE INDEX uc_users_user_name_ci ON users (UPPER(user_name));

ALTER TABLE users_authorities
    ADD CONSTRAINT fk_users_authorities_on_user FOREIGN KEY (owner_id) REFERENCES users (id);
