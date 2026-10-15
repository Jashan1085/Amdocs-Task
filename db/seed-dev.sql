-- Development fixtures only. Run once after schema.sql on an empty database.
-- Fixed SLA evaluation time: 2026-10-15 12:00:00+00 (17:30 Asia/Kolkata).
-- Duplicate business keys deliberately fail; the transaction prevents partial seeding.
BEGIN;

INSERT INTO sla_configuration
    (priority, policy_version, response_minutes, resolution_minutes, warning_percent)
VALUES
    ('CRITICAL', 1, 15, 120, 80),
    ('HIGH', 1, 30, 240, 80),
    ('MEDIUM', 1, 120, 720, 80),
    ('LOW', 1, 480, 2880, 80);

INSERT INTO customer
    (customer_number, customer_name, email, mobile_number, customer_type, city)
VALUES
    ('CUST1001', 'Demo Consumer', 'consumer@example.test', '+910000000001', 'CONSUMER', 'Mumbai'),
    ('CUST1002', 'Demo SME', 'sme@example.test', '+910000000002', 'SME', 'New Delhi'),
    ('CUST1003', 'Demo Enterprise', 'enterprise@example.test', '+910000000003', 'ENTERPRISE', 'Bengaluru');

INSERT INTO telecom_service
    (customer_id, service_code, service_name, service_type, region, activation_date)
SELECT c.customer_id, s.code, s.name, s.kind::service_type, s.region, DATE '2026-01-01'
FROM (VALUES
    ('CUST1001', 'SVC-MOB-001', 'Personal Mobile', 'MOBILE', 'West'),
    ('CUST1001', 'SVC-BB-001', 'Home Broadband', 'BROADBAND', 'West'),
    ('CUST1002', 'SVC-VPN-001', 'Office VPN', 'VPN', 'North'),
    ('CUST1003', 'SVC-ENT-001', 'Enterprise Link', 'ENTERPRISE_CONNECTIVITY', 'South'),
    ('CUST1003', 'SVC-CLD-001', 'Cloud Link', 'CLOUD_CONNECTIVITY', 'South')
) AS s(customer_number, code, name, kind, region)
JOIN customer c ON c.customer_number = s.customer_number;

INSERT INTO network_engineer
    (employee_code, engineer_name, specialization, region, experience_years, available)
VALUES
    ('ENG1008', 'Demo Core Engineer', 'Core Network', 'West', 9, TRUE),
    ('ENG1015', 'Demo RAN Engineer', 'RAN', 'North', 7, FALSE),
    ('ENG1021', 'Demo Broadband Engineer', 'Broadband', 'West', 6, TRUE),
    ('ENG1030', 'Demo IP Engineer', 'IP Network', 'South', 10, TRUE);

WITH fixtures(number, service_code, engineer_code, category, priority, status,
              age, response_age, resolution_age, closure_age, description) AS (
    VALUES
    ('TT-2026-000001', 'SVC-MOB-001', NULL, 'NETWORK_OUTAGE', 'CRITICAL', 'OPEN',
     INTERVAL '13 minutes', NULL::INTERVAL, NULL::INTERVAL, NULL::INTERVAL,
     'Open ticket approaching the response SLA deadline.'),
    ('TT-2026-000002', 'SVC-ENT-001', 'ENG1030', 'ENTERPRISE_LINK', 'HIGH', 'ASSIGNED',
     INTERVAL '5 hours', NULL, NULL, NULL,
     'Assigned ticket with breached response and resolution deadlines.'),
    ('TT-2026-000003', 'SVC-BB-001', 'ENG1021', 'BROADBAND', 'MEDIUM', 'IN_PROGRESS',
     INTERVAL '10 hours', INTERVAL '9 hours', NULL, NULL,
     'Acknowledged within response SLA; resolution is now at risk.'),
    ('TT-2026-000004', 'SVC-BB-001', 'ENG1021', 'SLOW_DATA', 'LOW', 'PENDING_CUSTOMER',
     INTERVAL '2 hours', INTERVAL '1 hour', NULL, NULL,
     'Waiting for customer diagnostics; SLA clock continues.'),
    ('TT-2026-000005', 'SVC-MOB-001', 'ENG1008', 'CALL_DROP', 'CRITICAL', 'ESCALATED',
     INTERVAL '3 hours', INTERVAL '170 minutes', NULL, NULL,
     'Acknowledged within response SLA; escalated after resolution breach.'),
    ('TT-2026-000006', 'SVC-CLD-001', 'ENG1030', 'NO_CONNECTIVITY', 'HIGH', 'RESOLVED',
     INTERVAL '4 hours', INTERVAL '225 minutes', INTERVAL '1 hour', NULL,
     'Resolved within SLA and awaiting service-desk closure.'),
    ('TT-2026-000007', 'SVC-VPN-001', 'ENG1015', 'OTHER', 'LOW', 'CLOSED',
     INTERVAL '24 hours', INTERVAL '23 hours', INTERVAL '4 hours', INTERVAL '2 hours',
     'Previously resolved and closed; engineer is currently unavailable.'),
    ('TT-2026-000008', 'SVC-MOB-001', NULL, 'SIM_ISSUE', 'MEDIUM', 'CANCELLED',
     INTERVAL '1 hour', NULL, NULL, NULL,
     'Customer cancelled an unassigned ticket.')
), reference_time AS (
    SELECT TIMESTAMPTZ '2026-10-15 12:00:00+00' AS at
)
INSERT INTO trouble_ticket (
    ticket_number, service_id, assigned_engineer_id, category, description,
    priority, severity, sla_policy_version, status, created_at, updated_at,
    response_deadline, resolution_deadline, first_response_at, resolved_at, closed_at
)
SELECT f.number, s.service_id, e.engineer_id, f.category::incident_category, f.description,
       p.priority, f.priority, p.policy_version, f.status::ticket_status,
       r.at - f.age, r.at,
       r.at - f.age + p.response_minutes * INTERVAL '1 minute',
       r.at - f.age + p.resolution_minutes * INTERVAL '1 minute',
       r.at - f.response_age, r.at - f.resolution_age, r.at - f.closure_age
FROM fixtures f
CROSS JOIN reference_time r
JOIN telecom_service s ON s.service_code = f.service_code
LEFT JOIN network_engineer e ON e.employee_code = f.engineer_code
JOIN sla_configuration p ON p.priority = f.priority::ticket_priority AND p.policy_version = 1;

-- Every ticket gets a complete path ending at its seeded current status.
INSERT INTO ticket_status_history
    (ticket_id, old_status, new_status, changed_by, changed_at, remarks)
SELECT t.ticket_id, h.old_status::ticket_status, h.new_status::ticket_status,
       CASE WHEN h.new_status IN ('IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED')
            THEN e.employee_code ELSE 'SYSTEM' END,
       h.changed_at, 'Development fixture: ' || h.new_status
FROM trouble_ticket t
LEFT JOIN network_engineer e ON e.engineer_id = t.assigned_engineer_id
CROSS JOIN LATERAL (
    SELECT NULL::TEXT, 'OPEN'::TEXT, t.created_at
    UNION ALL SELECT 'OPEN', 'ASSIGNED', t.created_at + INTERVAL '1 minute'
        WHERE t.assigned_engineer_id IS NOT NULL
    UNION ALL SELECT 'ASSIGNED', 'IN_PROGRESS', t.first_response_at
        WHERE t.first_response_at IS NOT NULL
    UNION ALL SELECT 'IN_PROGRESS', 'PENDING_CUSTOMER', t.first_response_at + INTERVAL '10 minutes'
        WHERE t.status = 'PENDING_CUSTOMER'
    UNION ALL SELECT 'IN_PROGRESS', 'ESCALATED', t.resolution_deadline + INTERVAL '1 minute'
        WHERE t.status = 'ESCALATED'
    UNION ALL SELECT 'IN_PROGRESS', 'RESOLVED', t.resolved_at
        WHERE t.resolved_at IS NOT NULL
    UNION ALL SELECT 'RESOLVED', 'CLOSED', t.closed_at
        WHERE t.closed_at IS NOT NULL
    UNION ALL SELECT 'OPEN', 'CANCELLED', t.created_at + INTERVAL '5 minutes'
        WHERE t.status = 'CANCELLED'
) AS h(old_status, new_status, changed_at)
WHERE t.ticket_number BETWEEN 'TT-2026-000001' AND 'TT-2026-000008'
ORDER BY t.ticket_id, h.changed_at;

COMMIT;
