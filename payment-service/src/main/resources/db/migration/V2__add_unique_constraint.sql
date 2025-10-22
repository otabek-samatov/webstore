ALTER TABLE payment
    ADD CONSTRAINT uc_payment_order UNIQUE (order_id);