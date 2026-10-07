package de.omarfourati.belegfluss.extraction;

import java.util.List;

/**
 * Turns an invoice into structured fields with an AI model.
 * Kept as an interface so the AI provider can be swapped or mocked in tests.
 */
public interface InvoiceExtractor {

    /** Reads the text layer of a digital PDF. */
    ExtractedInvoice extract(String invoiceText);

    /** Reads rendered page images (PNG) of a scanned invoice. */
    ExtractedInvoice extractFromImages(List<byte[]> pngPages);
}
