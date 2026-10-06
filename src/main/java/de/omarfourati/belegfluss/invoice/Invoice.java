package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String originalFilename;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] pdfContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    private String supplierName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private String currency;
    private String iban;
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Invoice() {
        // for JPA
    }

    public static Invoice received(String originalFilename, byte[] pdfContent) {
        Invoice invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.originalFilename = originalFilename;
        invoice.pdfContent = pdfContent;
        invoice.status = InvoiceStatus.RECEIVED;
        return invoice;
    }

    public void applyExtraction(ExtractedInvoice extracted) {
        this.supplierName = extracted.supplierName();
        this.invoiceNumber = extracted.invoiceNumber();
        this.invoiceDate = extracted.invoiceDate();
        this.dueDate = extracted.dueDate();
        this.netAmount = extracted.netAmount();
        this.vatAmount = extracted.vatAmount();
        this.grossAmount = extracted.grossAmount();
        this.currency = extracted.currency();
        this.iban = extracted.iban();
        this.errorMessage = null;
        this.status = InvoiceStatus.EXTRACTED;
    }

    public void markFailed(String reason) {
        this.errorMessage = reason;
        this.status = InvoiceStatus.FAILED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public byte[] getPdfContent() { return pdfContent; }
    public InvoiceStatus getStatus() { return status; }
    public String getSupplierName() { return supplierName; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public String getCurrency() { return currency; }
    public String getIban() { return iban; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
