package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.Invoice;
import de.omarfourati.belegfluss.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
