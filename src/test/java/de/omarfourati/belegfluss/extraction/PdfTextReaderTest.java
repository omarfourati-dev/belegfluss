package de.omarfourati.belegfluss.extraction;

import de.omarfourati.belegfluss.TestPdfs;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextReaderTest {

    private final PdfTextReader reader = new PdfTextReader();

    @Test
    void readsTextFromPdf() {
        String text = reader.read(TestPdfs.sampleInvoice());

        assertThat(text).contains("Rechnung Nr. RE-2026-0042").contains("Gesamt: 119,00 EUR");
    }

    @Test
    void rejectsPdfWithoutTextLayer() {
        assertThatThrownBy(() -> reader.read(TestPdfs.withLines()))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("no text layer");
    }

    @Test
    void rejectsNonPdfContent() {
        assertThatThrownBy(() -> reader.read("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("not a readable PDF");
    }

    @Test
    void limitsVeryLongText() {
        assertThat(PdfTextReader.limit("x".repeat(PdfTextReader.MAX_CHARS + 500)))
                .hasSize(PdfTextReader.MAX_CHARS);
        assertThat(PdfTextReader.limit("short")).isEqualTo("short");
    }
}
