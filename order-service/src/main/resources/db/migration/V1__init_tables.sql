CREATE SEQUENCE IF NOT EXISTS order_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE "order"
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
    CONSTRAINT pk_order PRIMARY KEY (id)
);