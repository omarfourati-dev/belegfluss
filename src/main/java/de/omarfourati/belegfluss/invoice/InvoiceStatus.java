package de.omarfourati.belegfluss.invoice;

public enum InvoiceStatus {
    /** PDF stored, waiting for AI extraction. */
    RECEIVED,
    /** Fields extracted successfully, ready for review. */
    EXTRACTED,
    /** Extraction failed, see {@code errorMessage}. */
    FAILED
}
