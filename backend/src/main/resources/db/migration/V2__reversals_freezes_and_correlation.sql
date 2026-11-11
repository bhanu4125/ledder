ALTER TABLE transactions
    DROP CONSTRAINT transactions_type_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check
        CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')),
    ADD COLUMN reversal_of_transaction_id UUID REFERENCES transactions(id),
    ADD CONSTRAINT uq_transaction_reversal UNIQUE (reversal_of_transaction_id);

ALTER TABLE accounts
    ADD COLUMN frozen_reason VARCHAR(240);

ALTER TABLE audit_log
    ADD COLUMN correlation_id VARCHAR(36);

ALTER TABLE security_events
    ADD COLUMN correlation_id VARCHAR(36);
