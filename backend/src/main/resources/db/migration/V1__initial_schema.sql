CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ADMIN', 'AUTHORISER', 'ACCOUNT_HOLDER', 'AUDITOR')),
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    lock_level INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT REFERENCES users(id),
    account_number VARCHAR(32) NOT NULL UNIQUE,
    balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    sender_account_id BIGINT REFERENCES accounts(id),
    receiver_account_id BIGINT REFERENCES accounts(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REJECTED', 'REVERSED')),
    idempotency_key VARCHAR(100) NOT NULL,
    requested_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by_user_id BIGINT REFERENCES users(id),
    decided_at TIMESTAMPTZ,
    memo VARCHAR(140),
    CONSTRAINT uq_transaction_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    entry_type VARCHAR(8) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    running_balance NUMERIC(19,2) NOT NULL,
    prev_hash CHAR(64) NOT NULL,
    entry_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ledger_account_created ON ledger_entries(account_id, created_at, id);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id),
    description VARCHAR(500) NOT NULL,
    ip_address VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    prev_hash CHAR(64) NOT NULL,
    entry_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    request_key VARCHAR(100) NOT NULL,
    endpoint VARCHAR(160) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key_endpoint UNIQUE (request_key, endpoint)
);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL,
    description VARCHAR(500) NOT NULL,
    ip_address VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chain_locks (
    chain_name VARCHAR(16) PRIMARY KEY
);
INSERT INTO chain_locks(chain_name) VALUES ('ledger'), ('audit');

-- A dedicated DB role must be created by the deployment operator. The schema owner
-- runs migrations; the runtime role receives SELECT/INSERT only on immutable logs.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledgerline_runtime') THEN
        REVOKE UPDATE, DELETE ON ledger_entries, audit_log FROM ledgerline_runtime;
        GRANT SELECT, INSERT ON ledger_entries, audit_log TO ledgerline_runtime;
    END IF;
END $$;
