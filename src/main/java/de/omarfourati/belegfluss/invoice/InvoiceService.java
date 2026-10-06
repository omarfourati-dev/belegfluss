package de.omarfourati.belegfluss.invoice;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final InvoiceRepository repository;
    private final ApplicationEventPublisher events;

    public InvoiceService(InvoiceRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /**
     * Stores the PDF and returns immediately. Extraction runs asynchronously
     * after the transaction has committed, see {@link InvoiceProcessor}.
     */
    @Transactional
    public Invoice receive(String originalFilename, byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new InvalidUploadException("Only PDF files are supported");
        }
        String filename = (originalFilename == null || originalFilename.isBlank())
                ? "invoice.pdf" : originalFilename;
        Invoice invoice = repository.save(Invoice.received(filename, content));
        events.publishEvent(new InvoiceReceivedEvent(invoice.getId()));
        return invoice;
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }
}
