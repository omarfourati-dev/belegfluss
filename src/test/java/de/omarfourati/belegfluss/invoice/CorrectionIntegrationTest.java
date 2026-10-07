package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.extraction.ExtractionException;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrectionIntegrationTest extends IntegrationTest {

    private static final Map<String, Object> CORRECT_FIELDS = Map.of(
            "supplierName", "Muster Buerobedarf GmbH",
            "invoiceNumber", "RE-2026-0042",
            "invoiceDate", "2026-10-01",
            "netAmount", 100.00,
            "vatAmount", 19.00,
            "grossAmount", 119.00,
            "currency", "EUR",
            "iban", "DE89 3704 0044 0532 0130 00");

    @Test
    void failedExtractionCanBeCompletedByHand() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        when(extractor.extract(anyString())).thenThrow(new ExtractionException("AI provider request failed (HTTP 503)"));
        String body = mvc.perform(as(employee, multipart("/api/invoices").file(pdf("r.pdf", TestPdfs.sampleInvoice()))))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(as(employee, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(CORRECT_FIELDS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andExpect(jsonPath("$.manuallyCorrected").value(true))
                .andExpect(jsonPath("$.iban").value("DE89370400440532013000"));
    }

    @Test
    void correctionIsAuditedAndTheEditorMayNotApprove() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        String otherApprover = loginAs(Role.APPROVER);
        UUID id = uploadExtractedInvoice(employee);

        var changed = new java.util.HashMap<>(CORRECT_FIELDS);
        changed.put("iban", "GB82WEST12345698765432");
        mvc.perform(as(approver, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value("GB82WEST12345698765432"));

        mvc.perform(as(employee, get("/api/invoices/{id}/history", id)))
                .andExpect(jsonPath("$[2].type").value("CORRECTED"))
                .andExpect(jsonPath("$[2].actor").value("Test APPROVER"))
                .andExpect(jsonPath("$[2].comment").value("Changed: iban"));

        // whoever changed the bank details must not be the one who releases the payment
        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Four-eyes principle"));
        mvc.perform(as(otherApprover, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isOk());
    }

    @Test
    void unchangedCorrectionLeavesNoTrace() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(employee, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(CORRECT_FIELDS))))
                .andExpect(jsonPath("$.manuallyCorrected").value(false));
        mvc.perform(as(employee, get("/api/invoices/{id}/history", id)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void approvedInvoicesCannotBeChanged() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        UUID id = uploadExtractedInvoice(employee);
        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id))).andExpect(status().isOk());

        mvc.perform(as(employee, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(CORRECT_FIELDS))))
                .andExpect(status().isConflict());
    }

    @Test
    void viewersCannotCorrectAndInputIsValidated() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String viewer = loginAs(Role.VIEWER);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(viewer, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(CORRECT_FIELDS))))
                .andExpect(status().isForbidden());

        var invalid = new java.util.HashMap<>(CORRECT_FIELDS);
        invalid.put("currency", "euro");
        invalid.put("grossAmount", -5);
        mvc.perform(as(employee, patch("/api/invoices/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))))
                .andExpect(status().isBadRequest());
    }
}
