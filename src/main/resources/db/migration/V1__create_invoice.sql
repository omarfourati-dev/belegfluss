CREATE TABLE invoice (
    id                UUID PRIMARY KEY,
    original_filename VARCHAR(255)   NOT NULL,
    pdf_content       BYTEA          NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    supplier_name     VARCHAR(255),
    invoice_number    VARCHAR(100),
    invoice_date      DATE,
    due_date          DATE,
    net_amount        NUMERIC(14, 2),
    vat_amount        NUMERIC(14, 2),
    gross_amount      NUMERIC(14, 2),
    currency          VARCHAR(3),
    iban              VARCHAR(34),
    error_message     VARCHAR(1000),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoice_created_at ON invoice (created_at DESC);
CREATE INDEX idx_invoice_supplier_number ON invoice (supplier_name, invoice_number);
