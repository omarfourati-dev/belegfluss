-- Move the PDF bytes out of the invoice table so listing invoices never loads documents.
CREATE TABLE invoice_document (
    invoice_id UUID PRIMARY KEY REFERENCES invoice (id) ON DELETE CASCADE,
    content    BYTEA NOT NULL
);

INSERT INTO invoice_document (invoice_id, content)
SELECT id, pdf_content FROM invoice;

ALTER TABLE invoice DROP COLUMN pdf_content;

-- Results of the automatic checks (duplicate, VAT, IBAN ...) as a JSON array of codes.
ALTER TABLE invoice ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]';

CREATE INDEX idx_invoice_supplier_lower ON invoice (lower(supplier_name));
