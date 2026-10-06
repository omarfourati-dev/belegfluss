package de.omarfourati.belegfluss.invoice;

/**
 * Lifecycle: RECEIVED -> EXTRACTED | FAILED, EXTRACTED -> APPROVED | REJECTED, APPROVED -> BOOKED.
 */
public enum InvoiceStatus {
    /** PDF stored, waiting for AI extraction. */
    RECEIVED,
    /** Fields extracted successfully, waiting for approval. */
    EXTRACTED,
    /** Extraction failed, see {@code errorMessage}. */
    FAILED,
    /** Approved by a second person (four-eyes principle). */
    APPROVED,
    /** Rejected by an approver, see {@code decisionComment}. */
    REJECTED,
    /** Booked by accounting. Final state. */
    BOOKED
}
