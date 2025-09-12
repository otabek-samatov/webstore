ALTER TABLE user_profile
    DROP CONSTRAINT fk_user_profile_on_address;

ALTER TABLE user_profile
    ADD city VARCHAR(255);

ALTER TABLE user_profile
    ADD country VARCHAR(255);

ALTER TABLE user_profile
    ADD region VARCHAR(255);

ALTER TABLE user_profile
    ADD street VARCHAR(255);

ALTER TABLE user_profile
    ALTER COLUMN city SET NOT NULL;

ALTER TABLE user_profile
    ALTER COLUMN country SET NOT NULL;

ALTER TABLE user_profile
    ALTER COLUMN region SET NOT NULL;

ALTER TABLE user_profile
    ALTER COLUMN street SET NOT NULL;

DROP TABLE address CASCADE;

ALTER TABLE user_profile
    DROP COLUMN address_id;

DROP SEQUENCE address_seq CASCADE;
