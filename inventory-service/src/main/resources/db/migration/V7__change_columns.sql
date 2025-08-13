ALTER TABLE inventory_change
    DROP CONSTRAINT fk_inventory_change_on_product_sku;

ALTER TABLE inventory_change
    DROP COLUMN product_sku;

ALTER TABLE inventory_change
    ADD product_sku VARCHAR;

ALTER TABLE inventory_change
    ADD CONSTRAINT FK_INVENTORY_CHANGE_ON_PRODUCT_SKU FOREIGN KEY (product_sku) REFERENCES inventory (product_sku);