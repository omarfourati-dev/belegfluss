package de.omarfourati.belegfluss.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The original PDF, stored apart from {@link Invoice} so lists stay light. */
@Entity
@Table(name = "invoice_document")
public class InvoiceDocument {

    @Id
    private UUID invoiceId;

    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private String contentType;

    protected InvoiceDocument() {
        // for JPA
    }

    InvoiceDocument(UUID invoiceId, byte[] content, String contentType) {
        this.invoiceId = invoiceId;
        this.content = content;
        this.contentType = contentType;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public byte[] getContent() { return content; }
    public String getContentType() { return contentType; }
}
