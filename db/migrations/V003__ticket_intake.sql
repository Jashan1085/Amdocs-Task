-- Additive milestone-4 foundation; apply through DatabaseMigrate only.
BEGIN;
CREATE SEQUENCE ticket_number_seq AS BIGINT START WITH 1 NO CYCLE;

CREATE TABLE notification (
    notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
    ticket_id BIGINT NOT NULL REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    event_type VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL CHECK (BTRIM(message) <> ''),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    processed_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notification_event CHECK (event_type IN
        ('TICKET_CREATED', 'TICKET_ASSIGNED', 'STATUS_CHANGED', 'SLA_WARNING', 'SLA_BREACHED'))
);
CREATE UNIQUE INDEX uq_notification_creation ON notification(ticket_id, customer_id)
    WHERE event_type = 'TICKET_CREATED';
CREATE INDEX idx_notification_customer_created ON notification(customer_id, created_at DESC, notification_id DESC);
CREATE INDEX idx_notification_pending ON notification(delivery_status, created_at)
    WHERE delivery_status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_notification_ticket ON notification(ticket_id);

CREATE TABLE audit_event (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    ticket_id BIGINT NOT NULL REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    action VARCHAR(40) NOT NULL CHECK (BTRIM(action) <> ''),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_ticket_time ON audit_event(ticket_id, occurred_at, audit_id);
CREATE INDEX idx_audit_actor_time ON audit_event(actor_user_id, occurred_at DESC);
CREATE TRIGGER protect_audit_event BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
COMMIT;
