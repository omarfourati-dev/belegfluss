package de.omarfourati.belegfluss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.omarfourati.belegfluss.extraction.ExtractedInvoice;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
import de.omarfourati.belegfluss.invoice.Invoice;
import de.omarfourati.belegfluss.invoice.InvoiceRepository;
import de.omarfourati.belegfluss.invoice.InvoiceStatus;
import de.omarfourati.belegfluss.user.Role;
import de.omarfourati.belegfluss.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base for API tests: real PostgreSQL, real security, only the LLM is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UserService userService;

    @Autowired
    protected InvoiceRepository invoices;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    protected InvoiceExtractor extractor;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE invoice_event, invoice_document, invoice, app_user CASCADE");
    }

    /** Creates a user with the given role and returns a valid access token for it. */
    protected String loginAs(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.de";
        userService.create(email, PASSWORD, "Test " + role, role);
        return login(email, PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }

    protected static <T extends MockHttpServletRequestBuilder> T as(String token, T request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    protected static MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, content);
    }

    /** Uploads the sample invoice and waits until extraction is done. */
    protected UUID uploadExtractedInvoice(String token) throws Exception {
        org.mockito.Mockito.when(extractor.extract(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(sampleExtraction());
        String body = mvc.perform(as(token, multipart("/api/invoices").file(pdf("rechnung.pdf", TestPdfs.sampleInvoice()))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());
        awaitStatus(id, InvoiceStatus.EXTRACTED);
        return id;
    }

    protected void awaitStatus(UUID id, InvoiceStatus expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(invoices.findById(id)).get().extracting(Invoice::getStatus).isEqualTo(expected));
    }

    protected static ExtractedInvoice sampleExtraction() {
        return new ExtractedInvoice(
                "Muster Buerobedarf GmbH", "RE-2026-0042", LocalDate.of(2026, 10, 1), null,
                new BigDecimal("100.00"), new BigDecimal("19.00"), new BigDecimal("119.00"),
                "EUR", "DE89370400440532013000");
    }
}
