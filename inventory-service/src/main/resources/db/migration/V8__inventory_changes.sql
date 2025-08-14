ALTER TABLE inventory_change
    DROP CONSTRAINT fk_inventory_change_on_product_sku;

ALTER TABLE inventory_change
    ADD inventory_id BIGINT;

ALTER TABLE inventory_change
    ALTER COLUMN inventory_id SET NOT NULL;

ALTER TABLE inventory_change
    ADD CONSTRAINT FK_INVENTORY_CHANGE_ON_INVENTORY FOREIGN KEY (inventory_id) REFERENCES inventory (id);

ALTER TABLE inventory_change
    DROP COLUMN product_sku;