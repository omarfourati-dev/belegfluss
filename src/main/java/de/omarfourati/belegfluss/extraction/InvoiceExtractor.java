package de.omarfourati.belegfluss.extraction;

/**
 * Turns the raw text of an invoice into structured fields.
 * Kept as an interface so the AI provider can be swapped or mocked in tests.
 */
public interface InvoiceExtractor {

    ExtractedInvoice extract(String invoiceText);
}
