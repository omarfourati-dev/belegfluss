package de.omarfourati.belegfluss.invoice;

import java.util.List;

public class WarningsNotAcknowledgedException extends RuntimeException {

    public WarningsNotAcknowledgedException(List<InvoiceWarning> warnings) {
        super("This invoice has warnings " + warnings + " - approving it needs a comment");
    }
}
