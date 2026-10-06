package de.omarfourati.belegfluss.auth;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void loginReturnsTokenThatIdentifiesTheUser() throws Exception {
        userService.create("Anna@Firma.de", PASSWORD, "Anna Approver", Role.APPROVER);

        String token = login("anna@firma.de", PASSWORD);

        mvc.perform(as(token, get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@firma.de"))
                .andExpect(jsonPath("$.displayName").value("Anna Approver"))
                .andExpect(jsonPath("$.roles[0]").value("APPROVER"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        userService.create("anna@firma.de", PASSWORD, "Anna", Role.EMPLOYEE);

        for (String email : new String[]{"anna@firma.de", "nobody@firma.de"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("E-mail or password is wrong"));
        }
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4Iiwicm9sZXMiOlsiQURNSU4iXX0.c2lnbmF0dXJl";

        mvc.perform(as(forged, get("/api/invoices"))).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadButNotUpload() throws Exception {
        String viewer = loginAs(Role.VIEWER);

        mvc.perform(as(viewer, get("/api/invoices"))).andExpect(status().isOk());
        mvc.perform(as(viewer, multipart("/api/invoices").file(pdf("r.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        String accountant = loginAs(Role.ACCOUNTANT);
        String admin = loginAs(Role.ADMIN);
        String newUser = json.writeValueAsString(Map.of(
                "email", "bob@firma.de", "password", "long-enough-password",
                "displayName", "Bob", "role", "APPROVER"));

        mvc.perform(as(accountant, post("/api/users").contentType(MediaType.APPLICATION_JSON).content(newUser)))
                .andExpect(status().isForbidden());
        mvc.perform(as(admin, post("/api/users").contentType(MediaType.APPLICATION_JSON).content(newUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("APPROVER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(as(admin, post("/api/users").contentType(MediaType.APPLICATION_JSON).content(newUser)))
                .andExpect(status().isConflict());

        login("bob@firma.de", "long-enough-password");
    }

    @Test
    void newUsersNeedAStrongPassword() throws Exception {
        String admin = loginAs(Role.ADMIN);
        String weak = json.writeValueAsString(Map.of(
                "email", "eve@firma.de", "password", "short", "displayName", "Eve", "role", "EMPLOYEE"));

        mvc.perform(as(admin, post("/api/users").contentType(MediaType.APPLICATION_JSON).content(weak)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void docsAndHealthArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/invoices/{id}/approve']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/")).andExpect(redirectedUrl("/swagger-ui.html"));
    }
}
