CREATE SEQUENCE IF NOT EXISTS inventory_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE inventory
(
    id               BIGINT       NOT NULL,
    version          INTEGER,
    product_id       BIGINT       NOT NULL,
    product_class    VARCHAR(255) NOT NULL,
    stock_level      DECIMAL(10,3) NOT NULL DEFAULT 0 CHECK (stock_level >= 0),
    reserved_stock   DECIMAL(10,3) NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    measurement_unit VARCHAR(255) NOT NULL,
    CONSTRAINT pk_inventory PRIMARY KEY (id)
);

ALTER TABLE inventory
    ADD CONSTRAINT uc_inventory_product_id UNIQUE (product_id, product_class);

CREATE INDEX idx_inventory_product_id ON inventory (product_id, product_class);