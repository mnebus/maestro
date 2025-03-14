-- table required by db-scheduler
create table scheduled_tasks
(
    task_name            TEXT                     NOT NULL,
    task_instance        TEXT                     NOT NULL,
    task_data            bytea,
    execution_time       TIMESTAMP WITH TIME ZONE NOT NULL,
    picked               BOOLEAN                  NOT NULL,
    picked_by            TEXT,
    last_success         TIMESTAMP WITH TIME ZONE,
    last_failure         TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat       TIMESTAMP WITH TIME ZONE,
    version              BIGINT                   NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);


-- tables for nimble workflow
CREATE TABLE workflow
(
    id                  VARCHAR PRIMARY KEY,
    scheduled_event_id  VARCHAR,
    started_event_id    VARCHAR,
    completed_event_id  VARCHAR,
    class_name          VARCHAR,
    input               bytea,
    output              bytea,
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_activity
(
    workflow_id         VARCHAR NOT NULL,
    name                VARCHAR NOT NULL,
    started_event_id    VARCHAR,
    completed_event_id  VARCHAR,
    output              bytea,
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_signal
(
    workflow_id         VARCHAR NOT NULL,
    name                VARCHAR NOT NULL,
    waiting_event_id    VARCHAR,
    received_event_id   VARCHAR,
    value               bytea,
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_condition
(
    workflow_id         VARCHAR NOT NULL,
    identifier          VARCHAR NOT NULL,
    waiting_event_id    VARCHAR,
    satisfied_event_id  VARCHAR,
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_sleep
(
    workflow_id         VARCHAR NOT NULL,
    identifier          VARCHAR NOT NULL,
    started_event_id    VARCHAR,
    completed_event_id  VARCHAR,
    duration_in_millis  bigint,
    created             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_event
(
    id              VARCHAR PRIMARY KEY,
    workflow_id     VARCHAR NOT NULL,
    category        VARCHAR,
    status          VARCHAR,
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE VIEW v_event_function_identifier AS
(
SELECT name AS identifier, started_event_id AS event_id
FROM workflow_activity
UNION
SELECT name AS identifier, completed_event_id AS event_id
FROM workflow_activity
UNION
SELECT identifier, waiting_event_id AS event_id
FROM workflow_condition
UNION
SELECT identifier, satisfied_event_id AS event_id
FROM workflow_condition
UNION
SELECT name, waiting_event_id AS event_id
FROM workflow_signal
UNION
SELECT name, received_event_id AS event_id
FROM workflow_signal
UNION
SELECT identifier, started_event_id AS event_id
FROM workflow_sleep
UNION
SELECT identifier, completed_event_id AS event_id
FROM workflow_sleep
    );

CREATE OR REPLACE VIEW v_workflow_event as
SELECT workflow_event.*,
       vfi.identifier as function_name
FROM workflow_event
         INNER JOIN v_event_function_identifier vfi
                    ON workflow_event.id = vfi.event_id;

-- end tables required for nimble


DROP TABLE IF EXISTS event;

CREATE TYPE status AS ENUM ('STARTED', 'COMPLETED', 'FAILED', 'RECEIVED', 'UNSATISFIED');
CREATE TYPE category AS ENUM ('WORKFLOW', 'ACTIVITY', 'SIGNAL', 'AWAIT', 'SLEEP');

CREATE TABLE event
(
    id                 VARCHAR PRIMARY KEY,
    workflow_id        VARCHAR   NOT NULL,
    category           category  NOT NULL,
    status             status    NOT NULL,
    data               JSON,
    class_name         VARCHAR,
    function_name      VARCHAR,
    timestamp          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_number BIGINT,
    sequence_number    BIGINT    NOT NULL,
    metadata           JSON
);

CREATE UNIQUE INDEX event_unique_workflow_correlation_status ON event (workflow_id, correlation_number, status);
CREATE UNIQUE INDEX event_unique_workflow_sequence ON event (workflow_id, sequence_number);
CREATE INDEX idx_workflow_category_status ON event (workflow_id, category, status);
CREATE INDEX idx_workflow_correlation_status ON event (workflow_id, correlation_number, status);
CREATE INDEX idx_workflow_status_sequence ON event (workflow_id, status, sequence_number);
CREATE INDEX idx_workflow_category_sequence ON event (workflow_id, category, sequence_number);
CREATE INDEX idx_workflow_category_status_timestamp ON event (workflow_id, category, status, timestamp);


