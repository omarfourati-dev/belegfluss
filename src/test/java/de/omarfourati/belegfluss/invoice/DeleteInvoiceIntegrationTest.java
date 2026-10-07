package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins clean up old invoices; booked ones stay part of the accounting records. */
class DeleteInvoiceIntegrationTest extends IntegrationTest {

    @Autowired
    private InvoiceDocumentRepository documents;

    @Autowired
    private InvoiceEventRepository events;

    @Test
    void adminDeletesInvoiceWithDocumentAndHistory() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String admin = loginAs(Role.ADMIN);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(admin, delete("/api/invoices/{id}", id))).andExpect(status().isNoContent());

        mvc.perform(as(admin, get("/api/invoices/{id}", id))).andExpect(status().isNotFound());
        assertThat(documents.findById(id)).isEmpty();
        assertThat(events.findByInvoiceIdOrderByCreatedAtAsc(id)).isEmpty();
    }

    @Test
    void bookedInvoicesCannotBeDeleted() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        String admin = loginAs(Role.ADMIN);
        UUID id = uploadExtractedInvoice(employee);
        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"passt\"}"))).andExpect(status().isOk());
        mvc.perform(as(admin, post("/api/invoices/{id}/book", id))).andExpect(status().isOk());

        mvc.perform(as(admin, delete("/api/invoices/{id}", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Booked invoices cannot be deleted"));
        assertThat(invoices.findById(id)).isPresent();
    }

    @Test
    void onlyAdminsCanDelete() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(loginAs(Role.ACCOUNTANT), delete("/api/invoices/{id}", id))).andExpect(status().isForbidden());
        assertThat(invoices.findById(id)).isPresent();
    }

    @Test
    void unknownInvoiceIsNotFound() throws Exception {
        mvc.perform(as(loginAs(Role.ADMIN), delete("/api/invoices/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void invoiceStillBeingReadCannotBeDeleted() {
        Invoice invoice = Invoice.received("rechnung.pdf", UUID.randomUUID());

        assertThatThrownBy(invoice::requireDeletable)
                .isInstanceOf(InvalidInvoiceStateException.class)
                .hasMessageContaining("still being read");
    }
}
