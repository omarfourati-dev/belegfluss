package de.omarfourati.belegfluss.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One entry of an invoice's audit trail. Immutable once written. */
@Entity
@Table(name = "invoice_event")
public class InvoiceEvent {

    static final String SYSTEM = "System";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceEventType type;

    private UUID actorId;

    @Column(nullable = false)
    private String actorName;

    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    protected InvoiceEvent() {
        // for JPA
    }

    InvoiceEvent(UUID invoiceId, InvoiceEventType type, UUID actorId, String actorName, String comment) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.type = type;
        this.actorId = actorId;
        this.actorName = actorName;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public InvoiceEventType getType() { return type; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
