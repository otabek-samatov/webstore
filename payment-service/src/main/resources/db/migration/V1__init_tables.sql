CREATE SEQUENCE IF NOT EXISTS payment_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE IF NOT EXISTS refund_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE payment
(
    id             BIGINT       NOT NULL,
    order_id       BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    payment_status VARCHAR(255) NOT NULL,
    amount         DECIMAL(9, 2),
    version        INTEGER,
    CONSTRAINT pk_payment PRIMARY KEY (id)
);

CREATE TABLE refund
(
    id            BIGINT        NOT NULL,
    payment_id    BIGINT        NOT NULL,
    refund_amount DECIMAL(9, 2) NOT NULL,
    refund_status VARCHAR(255)  NOT NULL,
    version       INTEGER,
    CONSTRAINT pk_refund PRIMARY KEY (id)
);

CREATE INDEX idx_payment_order_id ON payment (order_id);

CREATE INDEX idx_payment_user_id ON payment (user_id);

ALTER TABLE refund
    ADD CONSTRAINT FK_REFUND_ON_PAYMENT FOREIGN KEY (payment_id) REFERENCES payment (id);