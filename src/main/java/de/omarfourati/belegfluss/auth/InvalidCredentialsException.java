package de.omarfourati.belegfluss.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail or password is wrong");
    }
}
