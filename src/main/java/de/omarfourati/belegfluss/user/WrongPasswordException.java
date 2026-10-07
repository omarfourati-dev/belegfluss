package de.omarfourati.belegfluss.user;

public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("The current password is wrong");
    }
}
