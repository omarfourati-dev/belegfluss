package de.omarfourati.belegfluss.invoice;

/** Findings of the automatic checks. Warnings never block, but approving needs a comment. */
public enum InvoiceWarning {
    MISSING_FIELDS("Supplier, invoice number or gross amount could not be read"),
    VAT_MISMATCH("Net amount plus VAT does not equal the gross amount"),
    UNUSUAL_VAT_RATE("VAT rate is not 0 %, 7 % or 19 %"),
    INVALID_IBAN("IBAN check digits are invalid"),
    IBAN_CHANGED("Supplier used a different IBAN before - possible payment fraud"),
    DUPLICATE("An invoice with this number from this supplier already exists");

    private final String message;

    InvoiceWarning(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
