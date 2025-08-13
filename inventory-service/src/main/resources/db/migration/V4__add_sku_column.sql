ALTER TABLE inventory
    ADD product_sku VARCHAR(255);

ALTER TABLE inventory
    ALTER COLUMN product_sku SET NOT NULL;

ALTER TABLE inventory_change
    ADD product_sku BIGINT;

ALTER TABLE inventory
    ADD CONSTRAINT uc_inventory_product_sku UNIQUE (product_sku);

CREATE UNIQUE INDEX idx_inventory_product_sku_unq ON inventory (product_sku);

ALTER TABLE inventory_change
    ADD CONSTRAINT FK_INVENTORY_CHANGE_ON_PRODUCT_SKU FOREIGN KEY (product_sku) REFERENCES inventory (id);

ALTER TABLE inventory
    DROP COLUMN product_class;

ALTER TABLE inventory
    DROP COLUMN product_id;
