-- Additive authentication schema. Applied explicitly by DatabaseMigrate.
CREATE TABLE app_user (
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE CHECK (username = LOWER(username)),
    display_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    locked_until TIMESTAMPTZ,
    auth_version BIGINT NOT NULL DEFAULT 0 CHECK (auth_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app_role (
    role_code VARCHAR(30) PRIMARY KEY CHECK (role_code IN
        ('CUSTOMER', 'SERVICE_DESK', 'NETWORK_ENGINEER', 'NETWORK_MANAGER'))
);
INSERT INTO app_role VALUES ('CUSTOMER'), ('SERVICE_DESK'), ('NETWORK_ENGINEER'), ('NETWORK_MANAGER');
CREATE TABLE user_role (
    user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    role_code VARCHAR(30) NOT NULL REFERENCES app_role(role_code) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_code)
);
CREATE INDEX idx_user_role_role ON user_role(role_code, user_id);
ALTER TABLE customer ADD COLUMN user_id BIGINT UNIQUE REFERENCES app_user(user_id) ON DELETE RESTRICT;
ALTER TABLE network_engineer ADD COLUMN user_id BIGINT UNIQUE REFERENCES app_user(user_id) ON DELETE RESTRICT;
CREATE TABLE login_history (
    history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT REFERENCES app_user(user_id) ON DELETE RESTRICT,
    attempted_username VARCHAR(100) NOT NULL,
    outcome VARCHAR(30) NOT NULL CHECK (outcome IN
        ('PASSWORD_FAILURE', 'LOCKED', 'OTP_FAILURE', 'SUCCESS', 'CAPTCHA_FAILURE', 'RESET_REQUESTED', 'RESET_SUCCESS')),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_login_history_user_time ON login_history(user_id, occurred_at DESC);
CREATE TABLE otp_challenge (
    challenge_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE RESTRICT,
    purpose VARCHAR(10) NOT NULL CHECK (purpose IN ('LOGIN', 'RESET')),
    binding_hash CHAR(64) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    auth_version BIGINT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at),
    CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);
CREATE INDEX idx_otp_user_purpose ON otp_challenge(user_id, purpose, created_at DESC);
CREATE INDEX idx_otp_expiry ON otp_challenge(expires_at) WHERE consumed_at IS NULL;
