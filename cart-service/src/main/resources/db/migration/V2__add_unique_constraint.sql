ALTER TABLE cart_item
    ADD CONSTRAINT uc_cartitem_cart_id UNIQUE (cart_id, product_sku);