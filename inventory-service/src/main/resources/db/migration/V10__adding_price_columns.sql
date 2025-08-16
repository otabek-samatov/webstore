ALTER TABLE inventory
    ADD sell_price DECIMAL(9, 2);

ALTER TABLE inventory
    ADD stock_price DECIMAL(9, 2);

ALTER TABLE inventory
    ALTER COLUMN sell_price SET NOT NULL;

ALTER TABLE inventory
    ALTER COLUMN stock_price SET NOT NULL;
