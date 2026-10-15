BEGIN;
ALTER TABLE notification ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN last_error_code VARCHAR(40)
    CHECK(last_error_code IS NULL OR last_error_code='DELIVERY_UNAVAILABLE');
CREATE INDEX idx_notification_retry ON notification(next_attempt_at,notification_id)
    WHERE delivery_status IN ('PENDING','PROCESSING');
CREATE TABLE notification_receipt (
    notification_id BIGINT PRIMARY KEY REFERENCES notification(notification_id) ON DELETE RESTRICT,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP' CHECK(channel='IN_APP'),
    delivered_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER protect_notification_receipt BEFORE UPDATE OR DELETE ON notification_receipt
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
CREATE TABLE network_node (
    node_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    node_code VARCHAR(60) NOT NULL UNIQUE CHECK(BTRIM(node_code)<>''),
    node_name VARCHAR(120) NOT NULL CHECK(BTRIM(node_name)<>''),
    region VARCHAR(80) NOT NULL CHECK(BTRIM(region)<>''),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE service_network_node (
    service_id BIGINT NOT NULL REFERENCES telecom_service(service_id) ON DELETE RESTRICT,
    node_id BIGINT NOT NULL REFERENCES network_node(node_id) ON DELETE RESTRICT,
    PRIMARY KEY(service_id,node_id)
);
CREATE INDEX idx_service_node_node ON service_network_node(node_id,service_id);
CREATE TABLE network_event (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_key UUID NOT NULL UNIQUE,
    node_id BIGINT NOT NULL REFERENCES network_node(node_id) ON DELETE RESTRICT,
    event_type VARCHAR(20) NOT NULL CHECK(event_type IN ('OUTAGE','DEGRADATION')),
    description VARCHAR(1000) NOT NULL CHECK(BTRIM(description)<>''),
    created_by BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK(processing_status IN ('PENDING','PROCESSING','PROCESSED','UNMAPPED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK(retry_count>=0),
    next_attempt_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error_code VARCHAR(40) CHECK(last_error_code IS NULL OR last_error_code='EVENT_PROCESSING_UNAVAILABLE')
);
CREATE INDEX idx_event_pending ON network_event(next_attempt_at,event_id)
    WHERE processing_status IN ('PENDING','PROCESSING');
CREATE INDEX idx_event_created ON network_event(created_at DESC,event_id DESC);
ALTER TABLE trouble_ticket ADD CONSTRAINT uq_ticket_service UNIQUE(ticket_id,service_id);
CREATE TABLE event_ticket (
    event_id BIGINT NOT NULL REFERENCES network_event(event_id) ON DELETE RESTRICT,
    service_id BIGINT NOT NULL REFERENCES telecom_service(service_id) ON DELETE RESTRICT,
    ticket_id BIGINT NOT NULL UNIQUE REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    PRIMARY KEY(event_id,service_id),
    FOREIGN KEY(ticket_id,service_id) REFERENCES trouble_ticket(ticket_id,service_id) ON DELETE RESTRICT
);
CREATE INDEX idx_event_ticket_service ON event_ticket(service_id,event_id);
CREATE TRIGGER protect_event_ticket BEFORE UPDATE OR DELETE ON event_ticket
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
COMMIT;
