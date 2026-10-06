package de.omarfourati.belegfluss.invoice;

import java.util.UUID;

/** Published after a status change has been committed; pushed to clients via Server-Sent Events. */
public record InvoiceStatusChanged(UUID invoiceId, InvoiceStatus status) {
}
