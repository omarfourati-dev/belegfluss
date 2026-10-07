package de.omarfourati.belegfluss.extraction;

/** The PDF has no text layer, e.g. a scan or photo: it has to be read as an image. */
public class NoTextLayerException extends ExtractionException {

    public NoTextLayerException() {
        super("PDF contains no text layer (scanned image?)");
    }
}
