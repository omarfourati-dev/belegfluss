package de.omarfourati.belegfluss.extraction;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured result of the AI extraction. The field descriptions are sent to the
 * model as part of the JSON schema, so they double as extraction instructions.
 */
public record ExtractedInvoice(
        @JsonPropertyDescription("Legal name of the company that issued the invoice")
        String supplierName,
        @JsonPropertyDescription("Invoice number exactly as printed")
        String invoiceNumber,
        @JsonPropertyDescription("Invoice date, ISO-8601 (yyyy-MM-dd)")
        LocalDate invoiceDate,
        @JsonPropertyDescription("Payment due date, ISO-8601 (yyyy-MM-dd), null if not stated")
        LocalDate dueDate,
        @JsonPropertyDescription("Total net amount without VAT")
        BigDecimal netAmount,
        @JsonPropertyDescription("Total VAT amount")
        BigDecimal vatAmount,
        @JsonPropertyDescription("Total gross amount including VAT")
        BigDecimal grossAmount,
        @JsonPropertyDescription("ISO-4217 currency code, e.g. EUR")
        String currency,
        @JsonPropertyDescription("Supplier IBAN without spaces, null if not stated")
        String iban
) {
}
