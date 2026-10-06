package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.Invoice;
import de.omarfourati.belegfluss.invoice.InvoiceStatus;
import de.omarfourati.belegfluss.invoice.InvoiceWarning;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String originalFilename,
        InvoiceStatus status,
        String supplierName,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        String currency,
        String iban,
        String errorMessage,
        String decisionComment,
        List<Warning> warnings,
        Instant createdAt,
        Instant updatedAt
) {

    static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getOriginalFilename(),
                invoice.getStatus(),
                invoice.getSupplierName(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getNetAmount(),
                invoice.getVatAmount(),
                invoice.getGrossAmount(),
                invoice.getCurrency(),
                invoice.getIban(),
                invoice.getErrorMessage(),
                invoice.getDecisionComment(),
                invoice.getWarnings().stream().map(Warning::of).toList(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    public record Warning(InvoiceWarning code, String message) {

        static Warning of(InvoiceWarning warning) {
            return new Warning(warning, warning.message());
        }
    }
}
