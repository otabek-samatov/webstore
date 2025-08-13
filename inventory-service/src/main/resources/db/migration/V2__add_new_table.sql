CREATE SEQUENCE IF NOT EXISTS inventory_change_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE inventory_change
(
    id            BIGINT                      NOT NULL,
    event_time    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    change_amount DECIMAL                     NOT NULL,
    event_type    VARCHAR(255)                NOT NULL,
    event_id      BIGINT                      NOT NULL,
    CONSTRAINT pk_inventory_change PRIMARY KEY (id)
);

ALTER TABLE inventory
    ALTER COLUMN reserved_stock TYPE DECIMAL USING (reserved_stock::DECIMAL);

ALTER TABLE inventory
    ALTER COLUMN stock_level TYPE DECIMAL USING (stock_level::DECIMAL);

