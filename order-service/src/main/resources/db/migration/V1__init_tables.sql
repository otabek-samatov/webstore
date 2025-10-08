CREATE SEQUENCE IF NOT EXISTS order_item_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS orders_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE order_item
(
    id           BIGINT        NOT NULL,
    version      INTEGER,
    product_sku  VARCHAR(255)  NOT NULL,
    unit_price   DECIMAL(9, 2) NOT NULL,
    quantity     BIGINT        NOT NULL,
    order_id     BIGINT        NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id            BIGINT                      NOT NULL,
    version       INTEGER,
    user_id       BIGINT                      NOT NULL,
    cart_id       BIGINT                      NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    total_amount  DECIMAL(9, 2)               NOT NULL,
    tax_amount    DECIMAL(9, 2)               NOT NULL,
    shipping_cost DECIMAL(9, 2)               NOT NULL,
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

ALTER TABLE order_item
    ADD CONSTRAINT FK_ORDER_ITEM_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (id);