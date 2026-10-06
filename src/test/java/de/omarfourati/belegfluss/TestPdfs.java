package de.omarfourati.belegfluss;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Builds small PDFs on the fly so tests do not depend on binary fixtures. */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] withLines(String... lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.setLeading(16);
                    content.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] sampleInvoice() {
        return withLines(
                "Muster Buerobedarf GmbH",
                "Rechnung Nr. RE-2026-0042",
                "Rechnungsdatum: 01.10.2026",
                "Netto: 100,00 EUR",
                "MwSt. 19%: 19,00 EUR",
                "Gesamt: 119,00 EUR",
                "IBAN: DE89 3704 0044 0532 0130 00");
    }
}
