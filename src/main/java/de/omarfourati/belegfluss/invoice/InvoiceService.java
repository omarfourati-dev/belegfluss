package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.auth.CurrentUser;
import de.omarfourati.belegfluss.extraction.EInvoiceParser;
import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    static final String PDF = "application/pdf";
    static final String XML = "application/xml";

    private final InvoiceRepository repository;
    private final InvoiceDocumentRepository documents;
    private final InvoiceEventRepository events;
    private final InvoiceChecks checks;
    private final ApplicationEventPublisher publisher;

    public InvoiceService(InvoiceRepository repository, InvoiceDocumentRepository documents,
                          InvoiceEventRepository events, InvoiceChecks checks, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.documents = documents;
        this.events = events;
        this.checks = checks;
        this.publisher = publisher;
    }

    /**
     * Stores the PDF or e-invoice XML and returns immediately. Extraction runs asynchronously
     * after the transaction has committed, see {@link InvoiceProcessor}.
     */
    @Transactional
    public Invoice receive(String originalFilename, byte[] content, CurrentUser user) {
        String contentType = detectType(content);
        String filename = (originalFilename == null || originalFilename.isBlank())
                ? (XML.equals(contentType) ? "invoice.xml" : "invoice.pdf") : originalFilename;
        Invoice invoice = repository.save(Invoice.received(filename, user.id()));
        documents.save(new InvoiceDocument(invoice.getId(), content, contentType));
        record(invoice.getId(), InvoiceEventType.UPLOADED, user, null);
        publisher.publishEvent(new InvoiceReceivedEvent(invoice.getId()));
        publisher.publishEvent(new InvoiceStatusChanged(invoice.getId(), invoice.getStatus()));
        return invoice;
    }

    /** Manual correction of the fields; reruns the checks and records which fields changed. */
    @Transactional
    public Invoice correct(UUID id, ExtractedInvoice fields, CurrentUser user) {
        Invoice invoice = get(id);
        List<String> changed = invoice.correct(fields, user.id());
        if (changed.isEmpty()) {
            return invoice;
        }
        invoice.setWarnings(checks.check(invoice));
        record(id, InvoiceEventType.CORRECTED, user, "Changed: " + String.join(", ", changed));
        publisher.publishEvent(new InvoiceStatusChanged(id, invoice.getStatus()));
        return invoice;
    }

    @Transactional
    public Invoice approve(UUID id, CurrentUser user, String comment) {
        return change(id, InvoiceEventType.APPROVED, user, comment, invoice -> invoice.approve(user.id(), comment));
    }

    @Transactional
    public Invoice reject(UUID id, CurrentUser user, String reason) {
        return change(id, InvoiceEventType.REJECTED, user, reason, invoice -> invoice.reject(user.id(), reason));
    }

    @Transactional
    public Invoice book(UUID id, CurrentUser user) {
        return change(id, InvoiceEventType.BOOKED, user, null, Invoice::book);
    }

    /** Removes the invoice with its document and history (database cascade). Booked invoices stay. */
    @Transactional
    public void delete(UUID id, CurrentUser user) {
        Invoice invoice = get(id);
        invoice.requireDeletable();
        repository.delete(invoice);
        // the history goes with the invoice, so the deletion itself is kept in the log
        log.info("Invoice {} ({}) deleted by user {}", id, invoice.getStatus(), user.id());
        publisher.publishEvent(new InvoiceDeleted(id));
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public InvoiceDocument document(UUID id) {
        return documents.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    static String detectType(byte[] content) {
        if (content != null && content.length >= PDF_MAGIC.length
                && Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            return PDF;
        }
        if (content != null && content.length > 0 && EInvoiceParser.looksLikeXml(content)) {
            return XML;
        }
        throw new InvalidUploadException("Only PDF invoices and e-invoices (XRechnung / ZUGFeRD XML) are supported");
    }

    /** Invoices ready for accounting export, oldest invoice date first. */
    @Transactional(readOnly = true)
    public List<Invoice> findForExport(List<InvoiceStatus> statuses) {
        return repository.findByStatusInOrderByInvoiceDateAscCreatedAtAsc(statuses);
    }

    @Transactional(readOnly = true)
    public List<InvoiceEvent> history(UUID id) {
        get(id);
        return events.findByInvoiceIdOrderByCreatedAtAsc(id);
    }

    private Invoice change(UUID id, InvoiceEventType type, CurrentUser user, String comment, Consumer<Invoice> action) {
        Invoice invoice = get(id);
        action.accept(invoice);
        record(id, type, user, comment);
        publisher.publishEvent(new InvoiceStatusChanged(id, invoice.getStatus()));
        return invoice;
    }

    private void record(UUID invoiceId, InvoiceEventType type, CurrentUser user, String comment) {
        events.save(new InvoiceEvent(invoiceId, type, user.id(), user.displayName(), comment));
    }
}
