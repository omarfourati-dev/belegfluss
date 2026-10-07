package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.InvoiceDeleted;
import de.omarfourati.belegfluss.invoice.InvoiceStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes invoice status changes to connected clients via Server-Sent Events,
 * so the UI updates as soon as the background extraction is done.
 */
@Component
public class InvoiceStatusStream {

    private static final Logger log = LoggerFactory.getLogger(InvoiceStatusStream.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        send(emitter, SseEmitter.event().name("ready").data("ok"));
        return emitter;
    }

    /** Only after commit: clients must never see a status that was rolled back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(InvoiceStatusChanged change) {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().name("invoice").data(change)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeleted(InvoiceDeleted deleted) {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().name("invoice-deleted").data(deleted)));
    }

    /** Comment line every 25 s keeps proxies from closing idle connections. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping")));
    }

    int subscriberCount() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE subscriber: {}", e.getMessage());
            emitters.remove(emitter);
        }
    }
}
