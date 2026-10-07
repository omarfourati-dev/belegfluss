package de.omarfourati.belegfluss.invoice;

/** Where an invoice's fields came from. */
public enum ExtractionSource {
    /** LLM on the text layer of a PDF. */
    AI_TEXT,
    /** LLM on rendered page images of a scanned PDF. */
    AI_VISION,
    /** Structured e-invoice XML (XRechnung, ZUGFeRD/Factur-X): exact, no AI involved. */
    E_INVOICE
}
