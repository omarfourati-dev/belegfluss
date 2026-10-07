package de.omarfourati.belegfluss.extraction;

/** Fields read from a structured e-invoice, plus the detected format for the UI and audit trail. */
public record EInvoice(ExtractedInvoice data, String format) {
}
