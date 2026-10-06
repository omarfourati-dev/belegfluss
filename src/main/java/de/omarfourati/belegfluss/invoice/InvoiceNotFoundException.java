package de.omarfourati.belegfluss.invoice;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID id) {
        super("Invoice " + id + " not found");
    }
}
