package de.omarfourati.belegfluss.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextReader {

    /** Upper bound for the text sent to the model, keeps token costs predictable. */
    static final int MAX_CHARS = 20_000;

    public String read(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).strip();
            if (text.isEmpty()) {
                throw new NoTextLayerException();
            }
            return limit(text);
        } catch (IOException e) {
            throw new ExtractionException("File is not a readable PDF", e);
        }
    }

    static String limit(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
