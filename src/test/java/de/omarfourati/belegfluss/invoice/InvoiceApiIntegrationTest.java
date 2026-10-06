package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.extraction.ExtractionException;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceApiIntegrationTest extends IntegrationTest {

    @Test
    void uploadedInvoiceIsExtractedInTheBackground() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        when(extractor.extract(anyString())).thenReturn(sampleExtraction());

        mvc.perform(as(employee, multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.originalFilename").value("rechnung.pdf"));

        UUID id = invoices.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.EXTRACTED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierName").value("Muster Buerobedarf GmbH"))
                .andExpect(jsonPath("$.invoiceNumber").value("RE-2026-0042"))
                .andExpect(jsonPath("$.invoiceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.grossAmount").value(119.00));

        mvc.perform(as(employee, get("/api/invoices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(as(employee, get("/api/invoices/{id}/document", id)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void failedExtractionIsStoredWithReason() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        when(extractor.extract(anyString())).thenThrow(new ExtractionException("AI provider request failed (HTTP 503)"));

        mvc.perform(as(employee, multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isAccepted());

        UUID id = invoices.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.errorMessage").value("AI provider request failed (HTTP 503)"));
    }

    @Test
    void unexpectedErrorsDoNotLeaveInvoiceStuck() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        when(extractor.extract(anyString())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(as(employee, multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isAccepted());

        UUID id = invoices.findAll().getFirst().getId();
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.errorMessage").value("Unexpected processing error"));
    }

    @Test
    void rejectsFilesThatAreNotPdfs() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);

        mvc.perform(as(employee, multipart("/api/invoices").file(pdf("notes.pdf", "just text".getBytes()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid upload"));

        assertThat(invoices.count()).isZero();
    }

    @Test
    void unknownInvoiceReturns404() throws Exception {
        String viewer = loginAs(Role.VIEWER);

        mvc.perform(as(viewer, get("/api/invoices/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invoice not found"));
    }
}
