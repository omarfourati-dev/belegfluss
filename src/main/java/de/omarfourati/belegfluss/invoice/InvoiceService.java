package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.auth.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class InvoiceService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final InvoiceRepository repository;
    private final InvoiceEventRepository events;
    private final ApplicationEventPublisher publisher;

    public InvoiceService(InvoiceRepository repository, InvoiceEventRepository events,
                          ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.events = events;
        this.publisher = publisher;
    }

    /**
     * Stores the PDF and returns immediately. Extraction runs asynchronously
     * after the transaction has committed, see {@link InvoiceProcessor}.
     */
    @Transactional
    public Invoice receive(String originalFilename, byte[] content, CurrentUser user) {
        if (content == null || content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new InvalidUploadException("Only PDF files are supported");
        }
        String filename = (originalFilename == null || originalFilename.isBlank())
                ? "invoice.pdf" : originalFilename;
        Invoice invoice = repository.save(Invoice.received(filename, content, user.id()));
        record(invoice.getId(), InvoiceEventType.UPLOADED, user, null);
        publisher.publishEvent(new InvoiceReceivedEvent(invoice.getId()));
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

    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
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
        return invoice;
    }

    private void record(UUID invoiceId, InvoiceEventType type, CurrentUser user, String comment) {
        events.save(new InvoiceEvent(invoiceId, type, user.id(), user.displayName(), comment));
    }
}
