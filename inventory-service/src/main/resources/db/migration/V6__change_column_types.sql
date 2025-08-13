
ALTER TABLE inventory
    DROP COLUMN reserved_stock;

ALTER TABLE inventory
    DROP COLUMN stock_level;

ALTER TABLE inventory_change
    DROP COLUMN change_amount;

ALTER TABLE inventory_change
    ADD change_amount BIGINT NOT NULL;

ALTER TABLE inventory
    ADD reserved_stock BIGINT NOT NULL;

ALTER TABLE inventory
    ADD stock_level BIGINT NOT NULL;