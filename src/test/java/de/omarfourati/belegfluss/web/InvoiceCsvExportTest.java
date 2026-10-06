package de.omarfourati.belegfluss.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCsvExportTest {

    @Test
    void emptyExportHasBomAndHeader() {
        String csv = InvoiceCsvExport.toCsv(List.of());

        assertThat(csv).startsWith(InvoiceCsvExport.BOM + "Belegdatum;Fälligkeit;Lieferant;");
        assertThat(csv.lines()).hasSize(1);
    }

    @Test
    void quotesTextAndEscapesQuotes() {
        assertThat(InvoiceCsvExport.text("Müller \"Bau\" GmbH")).isEqualTo("\"Müller \"\"Bau\"\" GmbH\"");
        assertThat(InvoiceCsvExport.text("A;B")).isEqualTo("\"A;B\"");
        assertThat(InvoiceCsvExport.text(null)).isEmpty();
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(InvoiceCsvExport.text("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(InvoiceCsvExport.text("+49 123")).isEqualTo("\"'+49 123\"");
    }

    @Test
    void removesLineBreaks() {
        assertThat(InvoiceCsvExport.text("Zeile1\nZeile2")).isEqualTo("\"Zeile1 Zeile2\"");
    }
}
