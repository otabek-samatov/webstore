-- Consumer-side idempotency store. Inserted in the same DB transaction as
-- the listener's business changes; the unique message_id PK makes redelivered
-- Kafka messages a structural no-op.

CREATE TABLE inbox_messages
(
    message_id     VARCHAR(255) PRIMARY KEY,
    version        INTEGER                  NOT NULL,
    aggregate_type VARCHAR(255)             NOT NULL,
    aggregate_id   VARCHAR(255),
    event_type     VARCHAR(255)             NOT NULL,
    topic_name     VARCHAR(255)             NOT NULL,
    partition_no   INTEGER,
    kafka_offset   BIGINT,
    payload        TEXT                     NOT NULL,
    status         VARCHAR(20)              NOT NULL DEFAULT 'RECEIVED',
    received_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE
);

-- Supports the cleanup query (PROCESSED rows ordered by processed_at).
CREATE INDEX idx_inbox_status_processed_at ON inbox_messages (status, processed_at)
    WHERE status = 'PROCESSED';

-- Supports lookups by (topic, aggregate) when debugging or reconciling.
CREATE INDEX idx_inbox_topic_aggregate ON inbox_messages (topic_name, aggregate_id);
