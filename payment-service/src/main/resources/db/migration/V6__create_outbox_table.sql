-- Each microservice creates this table in its own database.
-- Can be added as a Flyway/Liquibase migration in the starter's resources.

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    version        INTEGER                  NOT NULL,
    aggregate_type VARCHAR(255)             NOT NULL,
    aggregate_id   VARCHAR(255)             NOT NULL,
    event_type     VARCHAR(255)             NOT NULL,
    topic_name     VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    status         VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at   TIMESTAMP WITH TIME ZONE
);

-- Index for the poller query: find PENDING events ordered by creation time
CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at)
    WHERE status = 'PENDING';

-- Index for the recovery job: find stuck PROCESSING events
CREATE INDEX idx_outbox_processing ON outbox_events (status, created_at)
    WHERE status = 'PROCESSING';
