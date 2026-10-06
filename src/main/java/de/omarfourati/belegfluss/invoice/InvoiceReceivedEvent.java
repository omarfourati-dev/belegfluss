package de.omarfourati.belegfluss.invoice;

import java.util.UUID;

public record InvoiceReceivedEvent(UUID invoiceId) {
}
