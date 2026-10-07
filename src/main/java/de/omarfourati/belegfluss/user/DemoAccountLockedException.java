package de.omarfourati.belegfluss.user;

/** The public demo account keeps its documented password so every visitor can log in. */
public class DemoAccountLockedException extends RuntimeException {

    public DemoAccountLockedException() {
        super("The password of the public demo account cannot be changed");
    }
}
