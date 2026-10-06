package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.extraction.InvoiceExtractor;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "belegfluss.upload-token=s3cret-test-token")
@AutoConfigureMockMvc
@Testcontainers
class UploadTokenIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InvoiceExtractor extractor;

    private final MockMultipartFile file = new MockMultipartFile(
            "file", "rechnung.pdf", MediaType.APPLICATION_PDF_VALUE, TestPdfs.sampleInvoice());

    @Test
    void uploadWithoutTokenIsRejected() throws Exception {
        mvc.perform(multipart("/api/invoices").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void uploadWithWrongTokenIsRejected() throws Exception {
        mvc.perform(multipart("/api/invoices").file(file).header("X-Upload-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadWithTokenIsAccepted() throws Exception {
        mvc.perform(multipart("/api/invoices").file(file).header("X-Upload-Token", "s3cret-test-token"))
                .andExpect(status().isAccepted());
    }

    @Test
    void readEndpointsStayPublic() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isOk());
    }

    @Test
    void rootRedirectsToApiDocs() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/swagger-ui.html"));
    }
}
