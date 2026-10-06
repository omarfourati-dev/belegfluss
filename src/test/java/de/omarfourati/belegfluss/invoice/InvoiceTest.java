package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    @Test
    void newInvoiceStartsAsReceived() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1});

        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
    }

    @Test
    void successfulExtractionClearsPreviousError() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1});
        invoice.markFailed("timeout");

        invoice.applyExtraction(new ExtractedInvoice("ACME", "1", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("11"), "EUR", null));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.EXTRACTED);
        assertThat(invoice.getErrorMessage()).isNull();
        assertThat(invoice.getSupplierName()).isEqualTo("ACME");
    }
}
