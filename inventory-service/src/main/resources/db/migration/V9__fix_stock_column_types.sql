-- Fix data type mismatch between database DECIMAL and Java Long
-- Change stock_level and reserved_stock from DECIMAL(10,3) to BIGINT

ALTER TABLE inventory 
    ALTER COLUMN stock_level TYPE BIGINT USING FLOOR(stock_level),
    ALTER COLUMN reserved_stock TYPE BIGINT USING FLOOR(reserved_stock);

-- Update check constraints to work with BIGINT
ALTER TABLE inventory 
    DROP CONSTRAINT IF EXISTS inventory_stock_level_check,
    DROP CONSTRAINT IF EXISTS inventory_reserved_stock_check;

ALTER TABLE inventory 
    ADD CONSTRAINT inventory_stock_level_check CHECK (stock_level >= 0),
    ADD CONSTRAINT inventory_reserved_stock_check CHECK (reserved_stock >= 0);