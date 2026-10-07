package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Business metrics that the server monitoring (Prometheus, Grafana, alerts) relies on. */
// Spring Boot switches metric exporters off in tests unless asked
@AutoConfigureObservability
class MetricsIntegrationTest extends IntegrationTest {

    @Test
    void prometheusEndpointShowsInvoiceExtractionAndLoginMetrics() throws Exception {
        uploadExtractedInvoice(loginAs(Role.EMPLOYEE));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nobody@firma.de", "password", "wrong-password"))));

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("belegfluss_invoices{status=\"extracted\"} 1.0")))
                .andExpect(content().string(containsString("belegfluss_extractions_total{outcome=\"success\",source=\"ai_text\"}")))
                .andExpect(content().string(containsString("belegfluss_extraction_duration_seconds_count{source=\"ai_text\"}")))
                .andExpect(content().string(containsString("belegfluss_logins_total{outcome=\"success\"}")))
                .andExpect(content().string(containsString("belegfluss_logins_total{outcome=\"failed\"}")));
    }
}
