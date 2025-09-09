ALTER TABLE security_role
    ADD CONSTRAINT uc_security_role_role_type UNIQUE (role_type);