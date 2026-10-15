-- Approved initial six-entity schema. Run once against an empty database.
BEGIN;

CREATE TYPE ticket_priority AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE ticket_status AS ENUM (
    'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_CUSTOMER',
    'ESCALATED', 'RESOLVED', 'CLOSED', 'CANCELLED'
);
CREATE TYPE service_type AS ENUM (
    'MOBILE', 'BROADBAND', 'ENTERPRISE_CONNECTIVITY', 'VPN', 'CLOUD_CONNECTIVITY'
);
CREATE TYPE incident_category AS ENUM (
    'NETWORK_OUTAGE', 'CALL_DROP', 'SLOW_DATA', 'NO_CONNECTIVITY', 'SIM_ISSUE',
    'BILLING', 'BROADBAND', 'ROAMING', 'ENTERPRISE_LINK', 'OTHER'
);

CREATE TABLE customer (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_number VARCHAR(30) NOT NULL UNIQUE,
    customer_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    mobile_number VARCHAR(20) NOT NULL,
    customer_type VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_type CHECK (customer_type IN ('CONSUMER', 'SME', 'ENTERPRISE')),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_customer_name CHECK (BTRIM(customer_name) <> '')
);

CREATE TABLE telecom_service (
    service_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    service_code VARCHAR(40) NOT NULL UNIQUE,
    service_name VARCHAR(150) NOT NULL,
    service_type service_type NOT NULL,
    region VARCHAR(50) NOT NULL,
    activation_date DATE NOT NULL,
    service_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_customer FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id) ON DELETE RESTRICT,
    CONSTRAINT ck_service_status CHECK (service_status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

CREATE TABLE network_engineer (
    engineer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    engineer_name VARCHAR(150) NOT NULL,
    specialization VARCHAR(80) NOT NULL,
    region VARCHAR(50) NOT NULL,
    experience_years SMALLINT NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_engineer_experience CHECK (experience_years >= 0),
    CONSTRAINT ck_engineer_name CHECK (BTRIM(engineer_name) <> '')
);

CREATE TABLE sla_configuration (
    priority ticket_priority NOT NULL,
    policy_version INTEGER NOT NULL,
    response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,
    warning_percent SMALLINT NOT NULL DEFAULT 80,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sla_configuration PRIMARY KEY (priority, policy_version),
    CONSTRAINT ck_sla_version CHECK (policy_version > 0),
    CONSTRAINT ck_sla_durations CHECK (response_minutes > 0 AND resolution_minutes >= response_minutes),
    CONSTRAINT ck_sla_warning CHECK (warning_percent BETWEEN 1 AND 99)
);

CREATE TABLE trouble_ticket (
    ticket_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_number VARCHAR(40) NOT NULL UNIQUE,
    service_id BIGINT NOT NULL,
    assigned_engineer_id BIGINT,
    category incident_category NOT NULL,
    description TEXT NOT NULL,
    priority ticket_priority NOT NULL,
    severity VARCHAR(20) NOT NULL,
    sla_policy_version INTEGER NOT NULL,
    status ticket_status NOT NULL DEFAULT 'OPEN',
    response_deadline TIMESTAMPTZ NOT NULL,
    resolution_deadline TIMESTAMPTZ NOT NULL,
    first_response_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_service FOREIGN KEY (service_id)
        REFERENCES telecom_service (service_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_engineer FOREIGN KEY (assigned_engineer_id)
        REFERENCES network_engineer (engineer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_sla FOREIGN KEY (priority, sla_policy_version)
        REFERENCES sla_configuration (priority, policy_version) ON DELETE RESTRICT,
    CONSTRAINT ck_ticket_description CHECK (BTRIM(description) <> ''),
    CONSTRAINT ck_ticket_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_ticket_deadlines CHECK (
        response_deadline > created_at AND resolution_deadline >= response_deadline),
    CONSTRAINT ck_ticket_first_response CHECK (
        first_response_at IS NULL OR first_response_at >= created_at),
    CONSTRAINT ck_ticket_resolution CHECK (
        resolved_at IS NULL OR (first_response_at IS NOT NULL AND resolved_at >= first_response_at)),
    CONSTRAINT ck_ticket_resolved_status CHECK (
        (status IN ('RESOLVED', 'CLOSED')) = (resolved_at IS NOT NULL)),
    CONSTRAINT ck_ticket_closed_status CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_ticket_closure_time CHECK (closed_at IS NULL OR closed_at >= resolved_at),
    CONSTRAINT ck_ticket_assignment CHECK (
        status NOT IN ('ASSIGNED', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED')
        OR assigned_engineer_id IS NOT NULL),
    CONSTRAINT ck_ticket_row_version CHECK (row_version >= 0)
);

CREATE TABLE ticket_status_history (
    history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    old_status ticket_status,
    new_status ticket_status NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT,
    CONSTRAINT fk_history_ticket FOREIGN KEY (ticket_id)
        REFERENCES trouble_ticket (ticket_id) ON DELETE RESTRICT,
    CONSTRAINT ck_history_transition CHECK (
        (old_status IS NULL AND new_status = 'OPEN')
        OR (old_status IS NOT NULL AND old_status <> new_status)),
    CONSTRAINT ck_history_actor CHECK (BTRIM(changed_by) <> '')
);

CREATE INDEX idx_service_customer_status ON telecom_service (customer_id, service_status);
CREATE INDEX idx_engineer_available_skill_region
    ON network_engineer (specialization, region) WHERE available = TRUE;
CREATE INDEX idx_ticket_service_created ON trouble_ticket (service_id, created_at DESC);
CREATE INDEX idx_ticket_engineer_status ON trouble_ticket (assigned_engineer_id, status);
CREATE INDEX idx_ticket_status_priority_created ON trouble_ticket (status, priority, created_at);
CREATE INDEX idx_ticket_sla ON trouble_ticket (priority, sla_policy_version);
CREATE INDEX idx_ticket_response_due ON trouble_ticket (response_deadline)
    WHERE first_response_at IS NULL AND status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED');
CREATE INDEX idx_ticket_resolution_due ON trouble_ticket (resolution_deadline)
    WHERE status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED');
CREATE INDEX idx_history_ticket_time ON ticket_status_history (ticket_id, changed_at, history_id);
CREATE UNIQUE INDEX uq_history_initial_entry ON ticket_status_history (ticket_id) WHERE old_status IS NULL;

CREATE FUNCTION reject_record_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'UPDATE and DELETE are not allowed on table %', TG_TABLE_NAME;
    RETURN NULL;
END;
$$;

CREATE TRIGGER protect_ticket_status_history
    BEFORE UPDATE OR DELETE ON ticket_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
CREATE TRIGGER protect_sla_configuration
    BEFORE UPDATE OR DELETE ON sla_configuration
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();

COMMIT;
