package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.extraction.EInvoice;
import de.omarfourati.belegfluss.extraction.EInvoiceParser;
import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import de.omarfourati.belegfluss.extraction.ExtractionException;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
import de.omarfourati.belegfluss.extraction.NoTextLayerException;
import de.omarfourati.belegfluss.extraction.PdfPageRenderer;
import de.omarfourati.belegfluss.extraction.PdfTextReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
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
    private final PdfPageRenderer pageRenderer;
    private final EInvoiceParser eInvoiceParser;
    private final InvoiceExtractor extractor;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;

    public InvoiceProcessor(InvoiceRepository repository, InvoiceDocumentRepository documents,
                            InvoiceEventRepository events, InvoiceChecks checks, PdfTextReader pdfTextReader,
                            PdfPageRenderer pageRenderer, EInvoiceParser eInvoiceParser,
                            InvoiceExtractor extractor, ApplicationEventPublisher publisher,
                            PlatformTransactionManager txManager) {
        this.pageRenderer = pageRenderer;
        this.eInvoiceParser = eInvoiceParser;
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
        InvoiceDocument document = tx.execute(status -> documents.findById(invoiceId).orElse(null));
        if (document == null) {
            log.warn("Invoice {} disappeared before processing", invoiceId);
            return;
        }

        try {
            Result result = extract(document);
            if (result.data() == null) {
                throw new ExtractionException("Extractor returned no result");
            }
            String note = result.format() == null ? null : "E-invoice: " + result.format();
            update(invoiceId, InvoiceEventType.EXTRACTED, note, invoice -> {
                invoice.applyExtraction(result.data(), result.source(), result.format());
                invoice.setWarnings(checks.check(invoice));
            });
            log.info("Invoice {} extracted via {}", invoiceId, result.source());
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

    /**
     * Order of preference: structured e-invoice (exact, no AI cost), then the PDF text layer,
     * then page images for scans. The LLM is only asked when there is no e-invoice data.
     */
    private Result extract(InvoiceDocument document) {
        byte[] content = document.getContent();
        if (InvoiceService.XML.equals(document.getContentType())) {
            EInvoice eInvoice = eInvoiceParser.parseXml(content).orElseThrow(() -> new ExtractionException(
                    "XML file is not a supported e-invoice (XRechnung UBL/CII, ZUGFeRD/Factur-X)"));
            return new Result(eInvoice.data(), ExtractionSource.E_INVOICE, eInvoice.format());
        }
        Optional<EInvoice> embedded = eInvoiceParser.parseEmbedded(content);
        if (embedded.isPresent()) {
            return new Result(embedded.get().data(), ExtractionSource.E_INVOICE, embedded.get().format());
        }
        try {
            return new Result(extractor.extract(pdfTextReader.read(content)), ExtractionSource.AI_TEXT, null);
        } catch (NoTextLayerException scan) {
            return new Result(extractor.extractFromImages(pageRenderer.renderPages(content)),
                    ExtractionSource.AI_VISION, null);
        }
    }

    private record Result(ExtractedInvoice data, ExtractionSource source, String format) {
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
