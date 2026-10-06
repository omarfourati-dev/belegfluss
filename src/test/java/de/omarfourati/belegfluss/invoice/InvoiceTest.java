package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    private final UUID uploader = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    @Test
    void newInvoiceStartsAsReceived() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1}, uploader);

        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RECEIVED);
        assertThat(invoice.getUploadedBy()).isEqualTo(uploader);
    }

    @Test
    void successfulExtractionClearsPreviousError() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1}, uploader);
        invoice.markFailed("timeout");

        invoice.applyExtraction(extraction());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.EXTRACTED);
        assertThat(invoice.getErrorMessage()).isNull();
        assertThat(invoice.getSupplierName()).isEqualTo("ACME");
    }

    @Test
    void approvedInvoiceCanBeBooked() {
        Invoice invoice = extractedInvoice();

        invoice.approve(approver, "ok");
        invoice.book();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.BOOKED);
        assertThat(invoice.getDecidedBy()).isEqualTo(approver);
    }

    @Test
    void uploaderCannotApprove() {
        Invoice invoice = extractedInvoice();

        assertThatThrownBy(() -> invoice.approve(uploader, null)).isInstanceOf(FourEyesViolationException.class);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.EXTRACTED);
    }

    @Test
    void onlyExtractedInvoicesCanBeDecided() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1}, uploader);

        assertThatThrownBy(() -> invoice.approve(approver, null)).isInstanceOf(InvalidInvoiceStateException.class);
        assertThatThrownBy(() -> invoice.reject(approver, "no")).isInstanceOf(InvalidInvoiceStateException.class);
        assertThatThrownBy(invoice::book).isInstanceOf(InvalidInvoiceStateException.class);
    }

    @Test
    void rejectedInvoiceIsFinal() {
        Invoice invoice = extractedInvoice();
        invoice.reject(approver, "wrong price");

        assertThat(invoice.getDecisionComment()).isEqualTo("wrong price");
        assertThatThrownBy(() -> invoice.approve(approver, null)).isInstanceOf(InvalidInvoiceStateException.class);
        assertThatThrownBy(invoice::book).isInstanceOf(InvalidInvoiceStateException.class);
    }

    private Invoice extractedInvoice() {
        Invoice invoice = Invoice.received("a.pdf", new byte[]{1}, uploader);
        invoice.applyExtraction(extraction());
        return invoice;
    }

    private static ExtractedInvoice extraction() {
        return new ExtractedInvoice("ACME", "1", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("11"), "EUR", null);
    }
}
