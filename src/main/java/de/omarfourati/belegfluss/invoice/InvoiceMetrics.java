package de.omarfourati.belegfluss.invoice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Business metrics for Prometheus (/actuator/prometheus, only reachable inside the Docker network):
 * belegfluss_invoices{status}, belegfluss_extractions_total{source,outcome},
 * belegfluss_extraction_duration_seconds{source}.
 */
@Component
public class InvoiceMetrics {

    /** Source tag when the extraction failed before a source was known. */
    static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public InvoiceMetrics(MeterRegistry registry, InvoiceRepository repository) {
        this.registry = registry;
        for (InvoiceStatus status : InvoiceStatus.values()) {
            Gauge.builder("belegfluss.invoices", repository, r -> r.countByStatus(status))
                    .description("Invoices per status")
                    .tag("status", tag(status))
                    .register(registry);
        }
    }

    void extracted(ExtractionSource source, Duration duration) {
        counter(tag(source), "success").increment();
        Timer.builder("belegfluss.extraction.duration")
                .description("Time from start of processing to extracted fields")
                .tag("source", tag(source))
                .register(registry)
                .record(duration);
    }

    /** {@code outcome}: "failed" for expected reasons (not an invoice, AI error), "error" for bugs. */
    void failed(String outcome) {
        counter(UNKNOWN, outcome).increment();
    }

    private Counter counter(String source, String outcome) {
        return Counter.builder("belegfluss.extractions")
                .description("Finished extractions by source and outcome")
                .tag("source", source)
                .tag("outcome", outcome)
                .register(registry);
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
