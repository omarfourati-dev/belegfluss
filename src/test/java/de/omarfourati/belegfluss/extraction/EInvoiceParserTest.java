package de.omarfourati.belegfluss.extraction;

import de.omarfourati.belegfluss.TestPdfs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EInvoiceParserTest {

    private final EInvoiceParser parser = new EInvoiceParser();

    @Test
    void readsXRechnungInUblSyntax() {
        EInvoice invoice = parser.parseXml(TestPdfs.resource("/einvoices/xrechnung-ubl.xml")).orElseThrow();

        assertThat(invoice.format()).isEqualTo("XRechnung (UBL)");
        ExtractedInvoice d = invoice.data();
        assertThat(d.supplierName()).isEqualTo("Rheinland IT-Service GmbH");
        assertThat(d.invoiceNumber()).isEqualTo("XR-2026-0815");
        assertThat(d.invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(d.dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(d.netAmount()).isEqualByComparingTo("1000.00");
        assertThat(d.vatAmount()).isEqualByComparingTo("190.00");
        assertThat(d.grossAmount()).isEqualByComparingTo("1190.00");
        assertThat(d.currency()).isEqualTo("EUR");
        assertThat(d.iban()).isEqualTo("DE89370400440532013000");
    }

    @Test
    void readsZugferdInCiiSyntax() {
        EInvoice invoice = parser.parseXml(TestPdfs.resource("/einvoices/zugferd-cii.xml")).orElseThrow();

        assertThat(invoice.format()).isEqualTo("ZUGFeRD / Factur-X");
        ExtractedInvoice d = invoice.data();
        assertThat(d.supplierName()).isEqualTo("Bergische Bürotechnik KG");
        assertThat(d.invoiceNumber()).isEqualTo("ZF-4711");
        assertThat(d.invoiceDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(d.dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(d.netAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(d.vatAmount()).isEqualByComparingTo(new BigDecimal("47.50"));
        assertThat(d.grossAmount()).isEqualByComparingTo(new BigDecimal("297.50"));
        assertThat(d.iban()).isEqualTo("GB82WEST12345698765432");
    }

    @Test
    void findsTheInvoiceEmbeddedInAZugferdPdf() {
        byte[] pdf = TestPdfs.withEmbeddedXml(TestPdfs.resource("/einvoices/zugferd-cii.xml"), "factur-x.xml");

        EInvoice invoice = parser.parseEmbedded(pdf).orElseThrow();

        assertThat(invoice.format()).isEqualTo("ZUGFeRD / Factur-X");
        assertThat(invoice.data().invoiceNumber()).isEqualTo("ZF-4711");
    }

    @Test
    void ordinaryPdfHasNoEmbeddedInvoice() {
        assertThat(parser.parseEmbedded(TestPdfs.sampleInvoice())).isEmpty();
    }

    @Test
    void wellFormedXmlThatIsNoInvoiceIsIgnored() {
        assertThat(parser.parseXml(TestPdfs.resource("/einvoices/not-an-invoice.xml"))).isEmpty();
    }

    @Test
    void rejectsXxeAttacks() {
        assertThatThrownBy(() -> parser.parseXml(TestPdfs.resource("/einvoices/xxe-attack.xml")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("invalid or unsafe XML");
    }

    @Test
    void rejectsBrokenXml() {
        assertThatThrownBy(() -> parser.parseXml("<Invoice><ID>1".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void recognisesXmlByContent() {
        assertThat(EInvoiceParser.looksLikeXml("﻿  <?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(EInvoiceParser.looksLikeXml("%PDF-1.7".getBytes(StandardCharsets.UTF_8))).isFalse();
    }
}
