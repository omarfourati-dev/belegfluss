package de.omarfourati.belegfluss.invoice;

import java.util.UUID;

/** Published after an invoice has been deleted; pushed to clients via Server-Sent Events. */
public record InvoiceDeleted(UUID invoiceId) {
}
