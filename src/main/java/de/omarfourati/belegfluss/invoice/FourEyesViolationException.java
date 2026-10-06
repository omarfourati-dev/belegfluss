package de.omarfourati.belegfluss.invoice;

public class FourEyesViolationException extends RuntimeException {

    public FourEyesViolationException() {
        super("Four-eyes principle: invoices must be approved by someone other than the uploader");
    }
}
