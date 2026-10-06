package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import de.omarfourati.belegfluss.extraction.ExtractionException;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
import de.omarfourati.belegfluss.extraction.PdfTextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs the extraction pipeline in the background. The slow AI call happens
 * outside of any database transaction, so no connection is held while waiting.
 */
@Component
public class InvoiceProcessor {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProcessor.class);

    private final InvoiceRepository repository;
    private final InvoiceDocumentRepository documents;
    private final InvoiceEventRepository events;
    private final InvoiceChecks checks;
    private final PdfTextReader pdfTextReader;
    private final InvoiceExtractor extractor;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;

    public InvoiceProcessor(InvoiceRepository repository, InvoiceDocumentRepository documents,
                            InvoiceEventRepository events, InvoiceChecks checks, PdfTextReader pdfTextReader,
                            InvoiceExtractor extractor, ApplicationEventPublisher publisher,
                            PlatformTransactionManager txManager) {
        this.repository = repository;
        this.documents = documents;
        this.events = events;
        this.checks = checks;
        this.publisher = publisher;
        this.pdfTextReader = pdfTextReader;
        this.extractor = extractor;
        this.tx = new TransactionTemplate(txManager);
    }

    @Async
    @TransactionalEventListener
    public void onInvoiceReceived(InvoiceReceivedEvent event) {
        process(event.invoiceId());
    }

    void process(UUID invoiceId) {
        byte[] pdf = tx.execute(status -> documents.findById(invoiceId)
                .map(InvoiceDocument::getContent)
                .orElse(null));
        if (pdf == null) {
            log.warn("Invoice {} disappeared before processing", invoiceId);
            return;
        }

        try {
            ExtractedInvoice extracted = extractor.extract(pdfTextReader.read(pdf));
            if (extracted == null) {
                throw new ExtractionException("Extractor returned no result");
            }
            update(invoiceId, InvoiceEventType.EXTRACTED, null, invoice -> {
                invoice.applyExtraction(extracted);
                invoice.setWarnings(checks.check(invoice));
            });
            log.info("Invoice {} extracted", invoiceId);
        } catch (ExtractionException e) {
            log.warn("Invoice {} failed: {}", invoiceId, e.getMessage());
            update(invoiceId, InvoiceEventType.EXTRACTION_FAILED, e.getMessage(),
                    invoice -> invoice.markFailed(e.getMessage()));
        } catch (RuntimeException e) {
            // Never leave an invoice stuck in RECEIVED
            log.error("Invoice {} failed unexpectedly", invoiceId, e);
            String reason = "Unexpected processing error";
            update(invoiceId, InvoiceEventType.EXTRACTION_FAILED, reason, invoice -> invoice.markFailed(reason));
        }
    }

    private void update(UUID invoiceId, InvoiceEventType type, String comment, Consumer<Invoice> change) {
        tx.executeWithoutResult(status -> repository.findById(invoiceId).ifPresent(invoice -> {
            change.accept(invoice);
            String note = comment;
            if (note == null && !invoice.getWarnings().isEmpty()) {
                note = "Warnings: " + invoice.getWarnings();
            }
            events.save(new InvoiceEvent(invoiceId, type, null, InvoiceEvent.SYSTEM, note));
            publisher.publishEvent(new InvoiceStatusChanged(invoiceId, invoice.getStatus()));
        }));
    }
}
