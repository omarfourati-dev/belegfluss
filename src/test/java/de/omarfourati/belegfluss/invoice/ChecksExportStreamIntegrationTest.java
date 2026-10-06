package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChecksExportStreamIntegrationTest extends IntegrationTest {

    private static final String VALID_IBAN = "DE89370400440532013000";
    private static final String OTHER_VALID_IBAN = "GB82WEST12345698765432";

    @Test
    void cleanInvoiceHasNoWarnings() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = upload(employee, extraction("RE-1", VALID_IBAN, "119.00"));

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void secondInvoiceWithSameNumberIsFlaggedAsDuplicate() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        upload(employee, extraction("RE-1", VALID_IBAN, "119.00"));
        UUID second = upload(employee, extraction("RE-1", VALID_IBAN, "119.00"));

        mvc.perform(as(employee, get("/api/invoices/{id}", second)))
                .andExpect(jsonPath("$.warnings[0].code").value("DUPLICATE"))
                .andExpect(jsonPath("$.warnings[0].message").exists());
    }

    @Test
    void changedBankDetailsAreFlagged() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        upload(employee, extraction("RE-1", VALID_IBAN, "119.00"));
        UUID second = upload(employee, extraction("RE-2", OTHER_VALID_IBAN, "119.00"));

        mvc.perform(as(employee, get("/api/invoices/{id}", second)))
                .andExpect(jsonPath("$.warnings[0].code").value("IBAN_CHANGED"));
    }

    @Test
    void calculationAndIbanErrorsAreFlagged() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = upload(employee, extraction("RE-9", "DE89370400440532013001", "129.00"));

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.warnings[*].code").value(
                        org.hamcrest.Matchers.containsInAnyOrder("VAT_MISMATCH", "INVALID_IBAN")));
    }

    @Test
    void approvingAnInvoiceWithWarningsNeedsAComment() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        UUID id = upload(employee, extraction("RE-9", VALID_IBAN, "129.00"));

        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Comment required"));

        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Skonto abgezogen, geprüft\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void accountantExportsApprovedInvoicesAsGermanCsv() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        String accountant = loginAs(Role.ACCOUNTANT);
        UUID approved = upload(employee, extraction("RE-1", VALID_IBAN, "119.00"));
        upload(employee, extraction("RE-2", VALID_IBAN, "119.00"));
        mvc.perform(as(approver, post("/api/invoices/{id}/approve", approved))).andExpect(status().isOk());

        String csv = mvc.perform(as(accountant, get("/api/invoices/export.csv")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("belegfluss-export.csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv.lines()).hasSize(2);
        assertThat(csv).contains("01.10.2026;;\"Muster Buerobedarf GmbH\";\"RE-1\";100,00;19,00;119,00;\"EUR\"")
                .doesNotContain("RE-2");

        mvc.perform(as(employee, get("/api/invoices/export.csv"))).andExpect(status().isForbidden());
    }

    @Test
    void statusStreamIsOpenForLoggedInUsersOnly() throws Exception {
        String viewer = loginAs(Role.VIEWER);

        mvc.perform(as(viewer, get("/api/invoices/events")))
                .andExpect(request().asyncStarted());
        mvc.perform(get("/api/invoices/events")).andExpect(status().isUnauthorized());
    }

    private UUID upload(String token, ExtractedInvoice extracted) throws Exception {
        when(extractor.extract(anyString())).thenReturn(extracted);
        String body = mvc.perform(as(token, multipart("/api/invoices").file(pdf("r.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());
        awaitStatus(id, InvoiceStatus.EXTRACTED);
        return id;
    }

    private static ExtractedInvoice extraction(String number, String iban, String gross) {
        return new ExtractedInvoice("Muster Buerobedarf GmbH", number, LocalDate.of(2026, 10, 1), null,
                new BigDecimal("100.00"), new BigDecimal("19.00"), new BigDecimal(gross), "EUR", iban);
    }
}
