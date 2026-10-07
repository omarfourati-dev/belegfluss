package de.omarfourati.belegfluss.user;

public class CannotDisableYourselfException extends RuntimeException {

    public CannotDisableYourselfException() {
        super("You cannot disable your own account");
    }
}
