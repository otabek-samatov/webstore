-- Tracks the state of orchestration-based sagas executed inside order-service.
-- Each row is one running or completed saga instance.

CREATE TABLE saga_instance
(
    id            UUID PRIMARY KEY,
    version       INTEGER                  NOT NULL,
    saga_type     VARCHAR(255)             NOT NULL,
    current_step  VARCHAR(255),
    status        VARCHAR(20)              NOT NULL DEFAULT 'STARTED',
    error_message TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_saga_type_status_created ON saga_instance (saga_type, status, created_at);
