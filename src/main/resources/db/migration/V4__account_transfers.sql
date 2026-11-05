CREATE TABLE transfers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    debit_ledger_id BIGINT NOT NULL UNIQUE REFERENCES ledger(id),
    credit_ledger_id BIGINT NOT NULL UNIQUE REFERENCES ledger(id),
    CHECK (debit_ledger_id <> credit_ledger_id)
);
