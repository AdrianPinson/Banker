CREATE TABLE branch (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    open BOOLEAN NOT NULL
);
INSERT INTO branch (id, open) VALUES (1, TRUE);
CREATE TABLE accounts (
    id VARCHAR(16) PRIMARY KEY,
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    balance NUMERIC(15,2) NOT NULL CHECK (balance >= 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    CHECK (status = 'OPEN' OR balance = 0)
);
CREATE TABLE tickets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id VARCHAR(16) NOT NULL REFERENCES accounts(id),
    status VARCHAR(12) NOT NULL CHECK (status IN ('WAITING', 'SERVING', 'COMPLETED')),
    teller VARCHAR(40),
    active_slot INTEGER,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    served_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (account_id, active_slot),
    CHECK ((status IN ('WAITING', 'SERVING') AND active_slot IS NOT NULL AND active_slot = 1) OR
           (status = 'COMPLETED' AND active_slot IS NULL))
);
CREATE INDEX tickets_fifo ON tickets(status, id);
CREATE TABLE ledger (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id VARCHAR(16) NOT NULL REFERENCES accounts(id),
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('DEPOSIT', 'WITHDRAWAL', 'CLOSE_ACCOUNT')),
    amount NUMERIC(15,2) NOT NULL,
    balance_after NUMERIC(15,2) NOT NULL CHECK (balance_after >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((kind IN ('DEPOSIT', 'WITHDRAWAL') AND amount > 0) OR
           (kind = 'CLOSE_ACCOUNT' AND amount = 0))
);
CREATE INDEX ledger_account ON ledger(account_id, id);
CREATE TABLE commands (
    request_key VARCHAR(36) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    response TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
