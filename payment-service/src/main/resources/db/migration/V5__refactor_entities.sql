-- Align schema with the refactored CoreEntity-based JPA entities.

-- version is now NOT NULL (CoreEntity.@Version with nullable = false)
UPDATE payment
SET version = 0
WHERE version IS NULL;
ALTER TABLE payment
    ALTER COLUMN version SET NOT NULL;

UPDATE refund
SET version = 0
WHERE version IS NULL;
ALTER TABLE refund
    ALTER COLUMN version SET NOT NULL;

-- Refund no longer carries amount or status (RefundStatus enum removed)
ALTER TABLE refund
    DROP COLUMN refund_amount;
ALTER TABLE refund
    DROP COLUMN refund_status;

-- Refund -> Payment is now @OneToOne: at most one refund per payment
ALTER TABLE refund
    ADD CONSTRAINT uc_refund_payment UNIQUE (payment_id);

-- allocationSize changed from 1 to 50 (pooled sequence optimizer)
ALTER SEQUENCE payment_seq INCREMENT BY 50;
ALTER SEQUENCE refund_seq INCREMENT BY 50;
