-- Run only against the seeded development database. No successful changes persist.
BEGIN;

DO $$
DECLARE
    reference_time CONSTANT TIMESTAMPTZ := '2026-10-15 12:00:00+00';
BEGIN
    IF (SELECT COUNT(*) FROM customer) <> 3
       OR (SELECT COUNT(*) FROM telecom_service) <> 5
       OR (SELECT COUNT(*) FROM network_engineer) <> 4
       OR (SELECT COUNT(*) FROM sla_configuration) <> 4
       OR (SELECT COUNT(*) FROM trouble_ticket) <> 8
       OR (SELECT COUNT(*) FROM ticket_status_history) <> 25 THEN
        RAISE EXCEPTION 'Unexpected development fixture counts';
    END IF;

    IF (SELECT COUNT(DISTINCT status) FROM trouble_ticket) <> 8 THEN
        RAISE EXCEPTION 'Not all ticket statuses are represented';
    END IF;

    IF EXISTS (
        SELECT 1 FROM trouble_ticket t
        JOIN sla_configuration p ON (p.priority, p.policy_version) = (t.priority, t.sla_policy_version)
        WHERE t.response_deadline <> t.created_at + p.response_minutes * INTERVAL '1 minute'
           OR t.resolution_deadline <> t.created_at + p.resolution_minutes * INTERVAL '1 minute'
    ) THEN
        RAISE EXCEPTION 'Seeded deadlines do not match their SLA policy';
    END IF;

    IF EXISTS (
        SELECT 1 FROM trouble_ticket t
        WHERE NOT EXISTS (
            SELECT 1 FROM ticket_status_history h
            WHERE h.ticket_id = t.ticket_id AND h.old_status IS NULL
              AND h.new_status = 'OPEN' AND h.changed_at = t.created_at
        ) OR t.status IS DISTINCT FROM (
            SELECT h.new_status FROM ticket_status_history h
            WHERE h.ticket_id = t.ticket_id
            ORDER BY h.changed_at DESC, h.history_id DESC LIMIT 1
        )
    ) THEN
        RAISE EXCEPTION 'Initial or latest history is inconsistent';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (
            SELECT old_status, LAG(new_status) OVER (
                PARTITION BY ticket_id ORDER BY changed_at, history_id
            ) AS previous_status FROM ticket_status_history
        ) chain WHERE old_status IS DISTINCT FROM previous_status
    ) THEN
        RAISE EXCEPTION 'History transition chain is inconsistent';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM trouble_ticket t
        WHERE ticket_number = 'TT-2026-000001'
          AND response_deadline > reference_time
          AND reference_time >= created_at + (response_deadline - created_at) * 0.8
    ) OR NOT EXISTS (
        SELECT 1 FROM trouble_ticket WHERE ticket_number = 'TT-2026-000002'
        AND response_deadline < reference_time AND resolution_deadline < reference_time
    ) OR NOT EXISTS (
        SELECT 1 FROM trouble_ticket WHERE ticket_number = 'TT-2026-000003'
        AND first_response_at <= response_deadline AND resolution_deadline > reference_time
        AND reference_time >= created_at + (resolution_deadline - created_at) * 0.8
    ) THEN
        RAISE EXCEPTION 'Expected SLA demo scenarios are missing';
    END IF;

    BEGIN
        UPDATE trouble_ticket SET service_id = -1 WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Foreign key accepted an invalid service';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        UPDATE trouble_ticket SET status = 'ASSIGNED' WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Assigned ticket accepted a missing engineer';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE trouble_ticket SET status = 'RESOLVED' WHERE ticket_number = 'TT-2026-000003';
        RAISE EXCEPTION 'Resolved ticket accepted a missing resolution timestamp';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE customer SET customer_number = 'CUST1001' WHERE customer_number = 'CUST1002';
        RAISE EXCEPTION 'Duplicate customer number accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE trouble_ticket SET priority = 'URGENT' WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Invalid enum accepted';
    EXCEPTION WHEN invalid_text_representation THEN NULL;
    END;
    BEGIN
        UPDATE trouble_ticket SET sla_policy_version = 999 WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Missing SLA policy version accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO ticket_status_history (ticket_id, new_status, changed_by)
        SELECT ticket_id, 'OPEN', 'SYSTEM' FROM trouble_ticket WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Duplicate initial history accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        DELETE FROM trouble_ticket WHERE ticket_number = 'TT-2026-000001';
        RAISE EXCEPTION 'Ticket with history was deleted';
    EXCEPTION WHEN foreign_key_violation OR restrict_violation THEN NULL;
    END;

    -- Trigger failures use the default P0001 SQLSTATE. Check their exact message
    -- so a missing trigger cannot accidentally make the test pass.
    BEGIN
        UPDATE ticket_status_history SET remarks = 'tampered';
        RAISE EXCEPTION 'History update accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'UPDATE and DELETE are not allowed on table ticket_status_history' THEN RAISE; END IF;
    END;
    BEGIN
        DELETE FROM ticket_status_history;
        RAISE EXCEPTION 'History deletion accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'UPDATE and DELETE are not allowed on table ticket_status_history' THEN RAISE; END IF;
    END;
    BEGIN
        UPDATE sla_configuration SET response_minutes = 1;
        RAISE EXCEPTION 'SLA update accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'UPDATE and DELETE are not allowed on table sla_configuration' THEN RAISE; END IF;
    END;
    BEGIN
        DELETE FROM sla_configuration;
        RAISE EXCEPTION 'SLA deletion accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'UPDATE and DELETE are not allowed on table sla_configuration' THEN RAISE; END IF;
    END;

    RAISE NOTICE 'Development schema, fixtures, SLA scenarios and integrity checks passed';
END;
$$;

ROLLBACK;
