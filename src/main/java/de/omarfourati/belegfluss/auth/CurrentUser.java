package de.omarfourati.belegfluss.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The authenticated caller, taken from the JWT claims. */
public record CurrentUser(UUID id, String displayName) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("name"));
    }
}
