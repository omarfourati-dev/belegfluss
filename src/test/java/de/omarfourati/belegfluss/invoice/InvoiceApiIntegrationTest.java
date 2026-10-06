package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import de.omarfourati.belegfluss.extraction.ExtractionException;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InvoiceApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    InvoiceRepository repository;

    @MockitoBean
    InvoiceExtractor extractor;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void uploadedInvoiceIsExtractedInTheBackground() throws Exception {
        when(extractor.extract(contains("RE-2026-0042"))).thenReturn(new ExtractedInvoice(
                "Muster Buerobedarf GmbH", "RE-2026-0042", LocalDate.of(2026, 10, 1), null,
                new BigDecimal("100.00"), new BigDecimal("19.00"), new BigDecimal("119.00"),
                "EUR", "DE89370400440532013000"));

        mvc.perform(multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice())))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.originalFilename").value("rechnung.pdf"));

        UUID id = repository.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.EXTRACTED);

        mvc.perform(get("/api/invoices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierName").value("Muster Buerobedarf GmbH"))
                .andExpect(jsonPath("$.invoiceNumber").value("RE-2026-0042"))
                .andExpect(jsonPath("$.invoiceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.grossAmount").value(119.00));

        mvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/invoices/{id}/document", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void failedExtractionIsStoredWithReason() throws Exception {
        when(extractor.extract(anyString())).thenThrow(new ExtractionException("AI extraction failed: timeout"));

        mvc.perform(multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice())))
                .andExpect(status().isAccepted());

        UUID id = repository.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(get("/api/invoices/{id}", id))
                .andExpect(jsonPath("$.errorMessage").value("AI extraction failed: timeout"));
    }

    @Test
    void unexpectedErrorsDoNotLeaveInvoiceStuck() throws Exception {
        when(extractor.extract(anyString())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice())))
                .andExpect(status().isAccepted());

        UUID id = repository.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(get("/api/invoices/{id}", id))
                .andExpect(jsonPath("$.errorMessage").value("Unexpected processing error"));
    }

    @Test
    void rejectsFilesThatAreNotPdfs() throws Exception {
        mvc.perform(multipart("/api/invoices").file(pdf("notes.pdf", "just text".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid upload"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void unknownInvoiceReturns404() throws Exception {
        mvc.perform(get("/api/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invoice not found"));
    }

    @Test
    void exposesHealthAndOpenApi() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/invoices']").exists());
    }

    private static MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, content);
    }

    private void awaitStatus(UUID id, InvoiceStatus expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id)).get().extracting(Invoice::getStatus).isEqualTo(expected));
    }
}
