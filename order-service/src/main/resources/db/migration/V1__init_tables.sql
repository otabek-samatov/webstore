CREATE SEQUENCE IF NOT EXISTS order_item_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS orders_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE order_item
(
    id           BIGINT       NOT NULL,
    version INTEGER NOT NULL,
    product_sku  VARCHAR(255) NOT NULL,
    unit_price   DECIMAL      NOT NULL,
    quantity     BIGINT       NOT NULL,
    order_id     BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id            BIGINT                      NOT NULL,
    version       INTEGER NOT NULL,
    customer_id   BIGINT                      NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    tax_amount    DECIMAL                     NOT NULL,
    shipping_cost DECIMAL NOT NULL,
    order_status  VARCHAR(255)                NOT NULL,
    country       VARCHAR(255)                NOT NULL,
    region        VARCHAR(255)                NOT NULL,
    city          VARCHAR(255)                NOT NULL,
    street        VARCHAR(255)                NOT NULL,
    address_line  VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

ALTER TABLE order_item
    ADD CONSTRAINT uc_orderitem_order_id UNIQUE (order_id, product_sku);

CREATE INDEX idx_order_customer_id ON orders (customer_id);

CREATE INDEX idx_orderitem_product_sku ON order_item (product_sku);

ALTER TABLE order_item
    ADD CONSTRAINT FK_ORDER_ITEM_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);

CREATE INDEX idx_orderitem_order_id ON order_item (order_id);