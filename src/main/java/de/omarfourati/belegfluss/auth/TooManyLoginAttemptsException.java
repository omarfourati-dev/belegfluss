package de.omarfourati.belegfluss.auth;

import java.time.Duration;

public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed logins. Please wait " + retryAfter.toMinutes() + " minutes and try again.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
