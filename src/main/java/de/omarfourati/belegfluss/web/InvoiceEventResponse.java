package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.InvoiceEvent;
import de.omarfourati.belegfluss.invoice.InvoiceEventType;

import java.time.Instant;

public record InvoiceEventResponse(InvoiceEventType type, String actor, String comment, Instant at) {

    static InvoiceEventResponse from(InvoiceEvent event) {
        return new InvoiceEventResponse(event.getType(), event.getActorName(), event.getComment(), event.getCreatedAt());
    }
}
