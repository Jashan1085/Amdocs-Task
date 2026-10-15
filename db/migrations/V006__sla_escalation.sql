BEGIN;
CREATE TABLE escalation_recipient (
    escalation_level SMALLINT PRIMARY KEY CHECK (escalation_level BETWEEN 1 AND 3),
    recipient_name VARCHAR(100) NOT NULL CHECK (BTRIM(recipient_name)<>''),
    target_email VARCHAR(254) NOT NULL CHECK (BTRIM(target_email)<>''),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- Development placeholders; no email is sent by milestone 7.
INSERT INTO escalation_recipient VALUES
    (1,'Team Lead','team-lead@example.test',TRUE),
    (2,'Network Manager','network-manager@example.test',TRUE),
    (3,'Operations Manager','operations-manager@example.test',TRUE);
CREATE TABLE ticket_escalation_state (
    ticket_id BIGINT PRIMARY KEY REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    highest_level SMALLINT NOT NULL DEFAULT 0 CHECK (highest_level BETWEEN 0 AND 3),
    previous_operational_status ticket_status CHECK (previous_operational_status IN ('OPEN','ASSIGNED','IN_PROGRESS','PENDING_CUSTOMER'))
);
-- Recover only explicitly recorded prior states; never guess from timestamps/status.
INSERT INTO ticket_escalation_state(ticket_id,highest_level,previous_operational_status)
SELECT t.ticket_id,0,(SELECT h.old_status FROM ticket_status_history h WHERE h.ticket_id=t.ticket_id
    AND h.new_status='ESCALATED' AND h.old_status IN ('OPEN','ASSIGNED','IN_PROGRESS','PENDING_CUSTOMER')
    ORDER BY h.changed_at DESC,h.history_id DESC LIMIT 1)
FROM trouble_ticket t WHERE t.status='ESCALATED';
CREATE TABLE escalation_history (
    escalation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES trouble_ticket(ticket_id) ON DELETE RESTRICT,
    escalation_level SMALLINT NOT NULL REFERENCES escalation_recipient(escalation_level) ON DELETE RESTRICT,
    previous_operational_status ticket_status,
    response_state VARCHAR(20) NOT NULL CHECK (response_state IN ('ON_TRACK','AT_RISK','BREACHED','MET','NOT_APPLICABLE')),
    resolution_state VARCHAR(20) NOT NULL CHECK (resolution_state IN ('ON_TRACK','AT_RISK','BREACHED','MET','NOT_APPLICABLE')),
    response_completed BOOLEAN NOT NULL,
    resolution_completed BOOLEAN NOT NULL,
    escalated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(ticket_id,escalation_level)
);
CREATE INDEX idx_escalation_level_time ON escalation_history(escalation_level,escalated_at DESC);
CREATE TRIGGER protect_escalation_history BEFORE UPDATE OR DELETE ON escalation_history
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
ALTER TABLE notification ADD COLUMN escalation_recipient_level SMALLINT REFERENCES escalation_recipient(escalation_level) ON DELETE RESTRICT;
ALTER TABLE notification ADD COLUMN escalation_id BIGINT REFERENCES escalation_history(escalation_id) ON DELETE RESTRICT;
ALTER TABLE notification DROP CONSTRAINT ck_notification_recipient;
ALTER TABLE notification ADD CONSTRAINT ck_notification_recipient CHECK
    ((CASE WHEN customer_id IS NOT NULL THEN 1 ELSE 0 END)+(CASE WHEN engineer_id IS NOT NULL THEN 1 ELSE 0 END)
    +(CASE WHEN escalation_recipient_level IS NOT NULL THEN 1 ELSE 0 END)=1);
CREATE UNIQUE INDEX uq_escalation_customer_notification ON notification(escalation_id,customer_id) WHERE escalation_id IS NOT NULL AND customer_id IS NOT NULL;
CREATE UNIQUE INDEX uq_escalation_engineer_notification ON notification(escalation_id,engineer_id) WHERE escalation_id IS NOT NULL AND engineer_id IS NOT NULL;
CREATE UNIQUE INDEX uq_escalation_recipient_notification ON notification(escalation_id,escalation_recipient_level) WHERE escalation_id IS NOT NULL AND escalation_recipient_level IS NOT NULL;
ALTER TABLE notification DROP CONSTRAINT ck_notification_event;
ALTER TABLE notification ADD CONSTRAINT ck_notification_event CHECK (event_type IN
    ('TICKET_CREATED','TICKET_ASSIGNED','STATUS_CHANGED','SLA_WARNING','SLA_BREACHED','FEEDBACK_RECEIVED','PRIORITY_CHANGED'));
ALTER TABLE audit_event ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE audit_event ADD COLUMN actor_kind VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_actor CHECK
    ((actor_kind='USER' AND actor_user_id IS NOT NULL) OR (actor_kind='SYSTEM' AND actor_user_id IS NULL));
ALTER TABLE audit_event ADD COLUMN previous_priority ticket_priority;
ALTER TABLE audit_event ADD COLUMN new_priority ticket_priority;
ALTER TABLE audit_event ADD COLUMN previous_sla_version INTEGER;
ALTER TABLE audit_event ADD COLUMN new_sla_version INTEGER;
COMMIT;
