package de.omarfourati.belegfluss.user;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("A user with e-mail " + email + " already exists");
    }
}
