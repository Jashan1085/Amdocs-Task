BEGIN;
CREATE TABLE ticket_resolution (
    ticket_id BIGINT PRIMARY KEY REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    root_cause VARCHAR(1000) NOT NULL CHECK (BTRIM(root_cause) <> ''),
    resolution_text VARCHAR(4000) NOT NULL CHECK (BTRIM(resolution_text) <> ''),
    resolution_code VARCHAR(40) NOT NULL CHECK (resolution_code IN
        ('CONFIGURATION_FIXED','HARDWARE_REPLACED','SERVICE_RESTORED','CUSTOMER_GUIDANCE','OTHER')),
    resolver_engineer_id BIGINT NOT NULL REFERENCES network_engineer(engineer_id) ON DELETE RESTRICT,
    resolved_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_resolution_engineer_time ON ticket_resolution(resolver_engineer_id,resolved_at DESC);
CREATE TABLE ticket_cancellation (
    ticket_id BIGINT PRIMARY KEY REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    reason VARCHAR(1000) NOT NULL CHECK (BTRIM(reason) <> ''),
    cancelled_by_user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    cancelled_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cancellation_actor_time ON ticket_cancellation(cancelled_by_user_id,cancelled_at DESC);
CREATE TABLE ticket_feedback (
    ticket_id BIGINT PRIMARY KEY REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    customer_id BIGINT NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comments VARCHAR(2000),
    submitted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_feedback_customer_time ON ticket_feedback(customer_id,submitted_at DESC);
CREATE TRIGGER protect_ticket_resolution BEFORE UPDATE OR DELETE ON ticket_resolution
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
CREATE TRIGGER protect_ticket_cancellation BEFORE UPDATE OR DELETE ON ticket_cancellation
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
CREATE TRIGGER protect_ticket_feedback BEFORE UPDATE OR DELETE ON ticket_feedback
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
ALTER TABLE notification DROP CONSTRAINT ck_notification_event;
ALTER TABLE notification ADD CONSTRAINT ck_notification_event CHECK (event_type IN
    ('TICKET_CREATED','TICKET_ASSIGNED','STATUS_CHANGED','SLA_WARNING','SLA_BREACHED','FEEDBACK_RECEIVED'));
COMMIT;
