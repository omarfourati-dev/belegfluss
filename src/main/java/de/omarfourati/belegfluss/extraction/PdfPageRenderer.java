package de.omarfourati.belegfluss.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Renders the first pages of a scanned PDF to PNG so a vision-capable model can read them. */
@Component
public class PdfPageRenderer {

    /** Invoices rarely have their totals beyond page 3; this also caps token costs. */
    static final int MAX_PAGES = 3;
    private static final float DPI = 130f;

    public List<byte[]> renderPages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            List<byte[]> images = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                images.add(out.toByteArray());
            }
            if (images.isEmpty()) {
                throw new ExtractionException("PDF has no pages");
            }
            return images;
        } catch (IOException e) {
            throw new ExtractionException("File is not a readable PDF", e);
        }
    }
}
