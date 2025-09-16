CREATE SEQUENCE IF NOT EXISTS cart_item_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS cart_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE cart
(
    id            BIGINT                      NOT NULL,
    user_id       BIGINT                      NOT NULL,
    creation_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version       INTEGER,
    status        VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_cart PRIMARY KEY (id)
);

CREATE TABLE cart_item
(
    id          BIGINT        NOT NULL,
    version     INTEGER,
    cart_id     BIGINT        NOT NULL,
    product_sku VARCHAR(255)  NOT NULL,
    unit_price  DECIMAL(9, 2) NOT NULL,
    quantity    BIGINT        NOT NULL,
    CONSTRAINT pk_cart_item PRIMARY KEY (id)
);

ALTER TABLE cart_item
    ADD CONSTRAINT FK_CART_ITEM_ON_CART FOREIGN KEY (cart_id) REFERENCES cart (id);