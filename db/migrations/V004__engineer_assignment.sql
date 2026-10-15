-- Assignment rules and durable recipient/audit details. Never rewrites existing tickets.
BEGIN;
CREATE TABLE category_specialization (
    category incident_category PRIMARY KEY,
    specialization VARCHAR(80) NOT NULL CHECK (BTRIM(specialization) <> '')
);
INSERT INTO category_specialization(category,specialization) VALUES
    ('NETWORK_OUTAGE','Core Network'), ('CALL_DROP','RAN'), ('SLOW_DATA','RAN'),
    ('NO_CONNECTIVITY','IP Network'), ('SIM_ISSUE','Core Network'), ('BILLING','Billing'),
    ('BROADBAND','Broadband'), ('ROAMING','Core Network'), ('ENTERPRISE_LINK','IP Network'),
    ('OTHER','IP Network');

ALTER TABLE notification ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE notification ADD COLUMN engineer_id BIGINT REFERENCES network_engineer(engineer_id) ON DELETE RESTRICT;
ALTER TABLE notification ADD CONSTRAINT ck_notification_recipient
    CHECK ((customer_id IS NOT NULL) <> (engineer_id IS NOT NULL));
CREATE INDEX idx_notification_engineer_created ON notification(engineer_id,created_at DESC,notification_id DESC)
    WHERE engineer_id IS NOT NULL;

ALTER TABLE audit_event ADD COLUMN previous_engineer_id BIGINT REFERENCES network_engineer(engineer_id) ON DELETE RESTRICT;
ALTER TABLE audit_event ADD COLUMN new_engineer_id BIGINT REFERENCES network_engineer(engineer_id) ON DELETE RESTRICT;
ALTER TABLE audit_event ADD COLUMN reason VARCHAR(1000);
COMMIT;
