CREATE SEQUENCE IF NOT EXISTS address_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS security_role_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS user_profile_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE address
(
    id      BIGINT       NOT NULL,
    version INTEGER,
    country VARCHAR(255) NOT NULL,
    region  VARCHAR(255) NOT NULL,
    city    VARCHAR(255) NOT NULL,
    street  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_address PRIMARY KEY (id)
);

CREATE TABLE security_role
(
    id        BIGINT       NOT NULL,
    version   INTEGER,
    role_type VARCHAR(255) NOT NULL,
    CONSTRAINT pk_security_role PRIMARY KEY (id)
);

CREATE TABLE user_profile
(
    id            BIGINT       NOT NULL,
    version       INTEGER,
    user_id       BIGINT       NOT NULL,
    first_name    VARCHAR(255) NOT NULL,
    last_name     VARCHAR(255) NOT NULL,
    middle_name   VARCHAR(255),
    date_of_birth date,
    address_id    BIGINT       NOT NULL,
    CONSTRAINT pk_user_profile PRIMARY KEY (id)
);

CREATE TABLE users
(
    id               BIGINT                      NOT NULL,
    version          INTEGER,
    user_name        VARCHAR(255)                NOT NULL,
    password         VARCHAR(255)                NOT NULL,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    is_active        BOOLEAN,
    security_role_id BIGINT                      NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE user_profile
    ADD CONSTRAINT uc_user_profile_address UNIQUE (address_id);

ALTER TABLE user_profile
    ADD CONSTRAINT uc_user_profile_user UNIQUE (user_id);

ALTER TABLE users
    ADD CONSTRAINT uc_users_user_name UNIQUE (user_name);

CREATE INDEX idx_user_user_name ON users (user_name);

ALTER TABLE users
    ADD CONSTRAINT FK_USERS_ON_SECURITYROLE FOREIGN KEY (security_role_id) REFERENCES security_role (id);

ALTER TABLE user_profile
    ADD CONSTRAINT FK_USER_PROFILE_ON_ADDRESS FOREIGN KEY (address_id) REFERENCES address (id);

ALTER TABLE user_profile
    ADD CONSTRAINT FK_USER_PROFILE_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);