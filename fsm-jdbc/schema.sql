-- FSM Library — snapshot table DDL
-- Compatible with: PostgreSQL, H2 2.x, SQLite 3.x
-- MySQL note: remove "IF NOT EXISTS" from the CREATE INDEX statement on MySQL < 8.0.1

CREATE TABLE IF NOT EXISTS fsm_snapshots
(
    execution_id          VARCHAR(255) NOT NULL,
    machine_definition_id VARCHAR(255) NOT NULL,
    current_state_name    VARCHAR(255),
    failed_state_name     VARCHAR(255),
    failed_sub_step_name  VARCHAR(255),
    last_trigger_event    VARCHAR(255),
    attempt_number        INT          NOT NULL DEFAULT 1,

    -- Stored as ISO-8601 strings (e.g. "2024-01-15T10:42:01Z") to avoid
    -- timezone handling differences across JDBC drivers and databases.
    last_failed_at        VARCHAR(50),
    scheduled_retry_at    VARCHAR(50),
    captured_at           VARCHAR(50)  NOT NULL,

    last_error_message    TEXT,

    -- SnapshotStatus enum value: RUNNING, FAILED, RETRY_SCHEDULED, COMPLETED
    status                VARCHAR(50)  NOT NULL,

    -- Flat key=value text encoding of completedSubStepResults.
    -- Format mirrors FileSnapshotRepository's Properties serialization.
    completed_steps       TEXT,

    -- Incremented on every save. Useful for monitoring and as the basis
    -- for custom optimistic locking in distributed deployments.
    version               BIGINT       NOT NULL DEFAULT 1,

    CONSTRAINT pk_fsm_snapshots PRIMARY KEY (execution_id)
);

-- Index on status speeds up listPendingRetries() queries.
CREATE INDEX IF NOT EXISTS idx_fsm_snapshots_status ON fsm_snapshots (status);
