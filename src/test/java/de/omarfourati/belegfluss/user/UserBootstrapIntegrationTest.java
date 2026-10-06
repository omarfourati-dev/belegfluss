package de.omarfourati.belegfluss.user;

import de.omarfourati.belegfluss.TestcontainersConfig;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "belegfluss.admin.email=Chef@Firma.de",
        "belegfluss.admin.password=a-very-long-admin-password",
        "belegfluss.demo.enabled=true"
})
@Import(TestcontainersConfig.class)
class UserBootstrapIntegrationTest {

    @Autowired
    UserService userService;

    @MockitoBean
    InvoiceExtractor extractor;

    @Test
    void createsAdminAndReadOnlyDemoAccountOnStartup() {
        assertThat(userService.authenticate("chef@firma.de", "a-very-long-admin-password"))
                .get().extracting(AppUser::getRole).isEqualTo(Role.ADMIN);
        assertThat(userService.authenticate("demo@belegfluss.app", "demo-belegfluss"))
                .get().extracting(AppUser::getRole).isEqualTo(Role.VIEWER);
    }
}
