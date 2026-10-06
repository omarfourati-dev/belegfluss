CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));

-- Workflow: who uploaded the invoice (null for invoices created before login existed)
ALTER TABLE invoice ADD COLUMN uploaded_by UUID REFERENCES app_user (id);
ALTER TABLE invoice ADD COLUMN decided_by UUID REFERENCES app_user (id);
ALTER TABLE invoice ADD COLUMN decision_comment VARCHAR(500);

-- Audit trail: every state change of an invoice
CREATE TABLE invoice_event (
    id          UUID PRIMARY KEY,
    invoice_id  UUID         NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL,
    actor_id    UUID REFERENCES app_user (id),
    actor_name  VARCHAR(100) NOT NULL,
    comment     VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_invoice_event_invoice ON invoice_event (invoice_id, created_at);
