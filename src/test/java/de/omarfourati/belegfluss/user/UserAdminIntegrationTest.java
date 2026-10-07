package de.omarfourati.belegfluss.user;

import de.omarfourati.belegfluss.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access on request: the admin creates accounts and can disable them again. */
class UserAdminIntegrationTest extends IntegrationTest {

    @Test
    void disabledUserIsLockedOutAtOnceAndCanBeEnabledAgain() throws Exception {
        String admin = loginAs(Role.ADMIN);
        AppUser tester = userService.create("tester@firma.de", PASSWORD, "Tina Tester", Role.EMPLOYEE);
        String testerToken = login("tester@firma.de", PASSWORD);

        disable(admin, tester.getId(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // the token issued before is rejected right away, a new login fails
        mvc.perform(as(testerToken, get("/api/invoices"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "tester@firma.de", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        disable(admin, tester.getId(), true).andExpect(status().isOk());
        String newToken = login("tester@firma.de", PASSWORD);
        mvc.perform(as(newToken, get("/api/invoices"))).andExpect(status().isOk());
    }

    @Test
    void adminCannotDisableThemselves() throws Exception {
        String admin = loginAs(Role.ADMIN);
        UUID adminId = userService.findAll().getFirst().getId();

        disable(admin, adminId, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You cannot disable your own account"));
    }

    @Test
    void onlyAdminsCanDisableUsers() throws Exception {
        AppUser other = userService.create("other@firma.de", PASSWORD, "Otto", Role.EMPLOYEE);
        String accountant = loginAs(Role.ACCOUNTANT);

        disable(accountant, other.getId(), false).andExpect(status().isForbidden());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        disable(loginAs(Role.ADMIN), UUID.randomUUID(), false).andExpect(status().isNotFound());
    }

    private ResultActions disable(String token, UUID id, boolean enabled) throws Exception {
        return mvc.perform(as(token, patch("/api/users/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("enabled", enabled)))));
    }
}
