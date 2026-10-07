-- Documents can now be PDFs or e-invoice XML files (XRechnung, ZUGFeRD/Factur-X).
ALTER TABLE invoice_document ADD COLUMN content_type VARCHAR(50) NOT NULL DEFAULT 'application/pdf';

-- Where the extracted fields came from: AI_TEXT (LLM on the PDF text layer),
-- AI_VISION (LLM on page images of a scan) or E_INVOICE (structured XML, no AI).
ALTER TABLE invoice ADD COLUMN source VARCHAR(20);
ALTER TABLE invoice ADD COLUMN e_invoice_format VARCHAR(40);

-- Who last corrected fields by hand: may not approve the invoice (four-eyes principle).
ALTER TABLE invoice ADD COLUMN last_edited_by UUID REFERENCES app_user (id);

UPDATE invoice SET source = 'AI_TEXT' WHERE status <> 'RECEIVED' AND status <> 'FAILED';
