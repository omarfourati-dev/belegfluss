package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowIntegrationTest extends IntegrationTest {

    @Test
    void uploadApproveBookWithAuditTrail() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        String accountant = loginAs(Role.ACCOUNTANT);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Bestellung passt\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionComment").value("Bestellung passt"));

        mvc.perform(as(accountant, post("/api/invoices/{id}/book", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));

        mvc.perform(as(employee, get("/api/invoices/{id}/history", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].type").value("UPLOADED"))
                .andExpect(jsonPath("$[0].actor").value("Test EMPLOYEE"))
                .andExpect(jsonPath("$[1].type").value("EXTRACTED"))
                .andExpect(jsonPath("$[1].actor").value("System"))
                .andExpect(jsonPath("$[2].type").value("APPROVED"))
                .andExpect(jsonPath("$[2].actor").value("Test APPROVER"))
                .andExpect(jsonPath("$[3].type").value("BOOKED"))
                .andExpect(jsonPath("$[3].actor").value("Test ACCOUNTANT"));
    }

    @Test
    void uploaderCannotApproveOwnInvoice() throws Exception {
        String approver = loginAs(Role.APPROVER);
        UUID id = uploadExtractedInvoice(approver);

        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Four-eyes principle"));
    }

    @Test
    void employeesCannotApprove() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String otherEmployee = loginAs(Role.EMPLOYEE);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(otherEmployee, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotBookBeforeApproval() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String accountant = loginAs(Role.ACCOUNTANT);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(accountant, post("/api/invoices/{id}/book", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid status"));
    }

    @Test
    void rejectNeedsAReasonAndStoresIt() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String approver = loginAs(Role.APPROVER);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(approver, post("/api/invoices/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")))
                .andExpect(status().isBadRequest());

        mvc.perform(as(approver, post("/api/invoices/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Preis falsch\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Preis falsch"));

        mvc.perform(as(approver, post("/api/invoices/{id}/approve", id)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminInheritsAllRoles() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        String admin = loginAs(Role.ADMIN);
        UUID id = uploadExtractedInvoice(employee);

        mvc.perform(as(admin, post("/api/invoices/{id}/approve", id))).andExpect(status().isOk());
        mvc.perform(as(admin, post("/api/invoices/{id}/book", id))).andExpect(status().isOk());
    }
}
