package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String originalFilename;

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
    private UUID uploadedBy;
    private UUID decidedBy;
    private String decisionComment;

    @Enumerated(EnumType.STRING)
    private ExtractionSource source;

    private String eInvoiceFormat;
    private UUID lastEditedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<InvoiceWarning> warnings = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Invoice() {
        // for JPA
    }

    public static Invoice received(String originalFilename, UUID uploadedBy) {
        Invoice invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.originalFilename = originalFilename;
        invoice.uploadedBy = uploadedBy;
        invoice.status = InvoiceStatus.RECEIVED;
        return invoice;
    }

    public void applyExtraction(ExtractedInvoice extracted) {
        applyExtraction(extracted, ExtractionSource.AI_TEXT, null);
    }

    public void applyExtraction(ExtractedInvoice extracted, ExtractionSource source, String eInvoiceFormat) {
        this.source = source;
        this.eInvoiceFormat = eInvoiceFormat;
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

    public void setWarnings(List<InvoiceWarning> warnings) {
        this.warnings = new ArrayList<>(warnings);
    }

    public void markFailed(String reason) {
        this.errorMessage = reason;
        this.status = InvoiceStatus.FAILED;
    }

    /**
     * Manual correction of the extracted fields (also rescues a FAILED extraction).
     * Returns the names of the fields that actually changed.
     */
    public List<String> correct(ExtractedInvoice fields, UUID editorId) {
        if (status != InvoiceStatus.EXTRACTED && status != InvoiceStatus.FAILED) {
            throw new InvalidInvoiceStateException(
                    "Only invoices in status EXTRACTED or FAILED can be corrected (current: " + status + ")");
        }
        List<String> changed = new ArrayList<>();
        diff(changed, "supplierName", supplierName, fields.supplierName());
        diff(changed, "invoiceNumber", invoiceNumber, fields.invoiceNumber());
        diff(changed, "invoiceDate", invoiceDate, fields.invoiceDate());
        diff(changed, "dueDate", dueDate, fields.dueDate());
        diff(changed, "netAmount", netAmount, fields.netAmount());
        diff(changed, "vatAmount", vatAmount, fields.vatAmount());
        diff(changed, "grossAmount", grossAmount, fields.grossAmount());
        diff(changed, "currency", currency, fields.currency());
        diff(changed, "iban", iban, fields.iban());
        if (changed.isEmpty() && status == InvoiceStatus.EXTRACTED) {
            return changed;
        }
        ExtractionSource previousSource = source;
        String previousFormat = eInvoiceFormat;
        applyExtraction(fields, previousSource, previousFormat);
        this.lastEditedBy = editorId;
        return changed;
    }

    private static void diff(List<String> changed, String name, Object before, Object after) {
        boolean same = before instanceof BigDecimal b && after instanceof BigDecimal a
                ? b.compareTo(a) == 0
                : Objects.equals(before, after);
        if (!same) {
            changed.add(name);
        }
    }

    /**
     * Four-eyes principle: neither the uploader nor the person who last corrected the
     * fields may approve - otherwise someone could change the IBAN and wave it through.
     */
    public void approve(UUID approverId, String comment) {
        requireStatus(InvoiceStatus.EXTRACTED, "approved");
        if (approverId.equals(uploadedBy) || approverId.equals(lastEditedBy)) {
            throw new FourEyesViolationException();
        }
        if (!warnings.isEmpty() && (comment == null || comment.isBlank())) {
            throw new WarningsNotAcknowledgedException(warnings);
        }
        decide(InvoiceStatus.APPROVED, approverId, comment);
    }

    public void reject(UUID approverId, String reason) {
        requireStatus(InvoiceStatus.EXTRACTED, "rejected");
        decide(InvoiceStatus.REJECTED, approverId, reason);
    }

    public void book() {
        requireStatus(InvoiceStatus.APPROVED, "booked");
        this.status = InvoiceStatus.BOOKED;
    }

    private void decide(InvoiceStatus newStatus, UUID actorId, String comment) {
        this.status = newStatus;
        this.decidedBy = actorId;
        this.decisionComment = comment;
    }

    private void requireStatus(InvoiceStatus expected, String action) {
        if (status != expected) {
            throw new InvalidInvoiceStateException(
                    "Only invoices in status " + expected + " can be " + action + " (current: " + status + ")");
        }
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
    public UUID getUploadedBy() { return uploadedBy; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
    public List<InvoiceWarning> getWarnings() { return List.copyOf(warnings); }
    public ExtractionSource getSource() { return source; }
    public String getEInvoiceFormat() { return eInvoiceFormat; }
    public UUID getLastEditedBy() { return lastEditedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
