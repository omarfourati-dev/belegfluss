package de.omarfourati.belegfluss.invoice;

import de.omarfourati.belegfluss.IntegrationTest;
import de.omarfourati.belegfluss.TestPdfs;
import de.omarfourati.belegfluss.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EInvoiceAndScanIntegrationTest extends IntegrationTest {

    @Test
    void xrechnungIsReadExactlyWithoutAi() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = upload(employee, xml("xrechnung.xml", "/einvoices/xrechnung-ubl.xml"));
        awaitStatus(id, InvoiceStatus.EXTRACTED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.source").value("E_INVOICE"))
                .andExpect(jsonPath("$.eInvoiceFormat").value("XRechnung (UBL)"))
                .andExpect(jsonPath("$.supplierName").value("Rheinland IT-Service GmbH"))
                .andExpect(jsonPath("$.grossAmount").value(1190.00))
                .andExpect(jsonPath("$.warnings.length()").value(0));
        mvc.perform(as(employee, get("/api/invoices/{id}/history", id)))
                .andExpect(jsonPath("$[1].comment").value("E-invoice: XRechnung (UBL)"));
        mvc.perform(as(employee, get("/api/invoices/{id}/document", id)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));

        verify(extractor, never()).extract(anyString());
        verify(extractor, never()).extractFromImages(anyList());
    }

    @Test
    void zugferdPdfUsesTheEmbeddedXmlInsteadOfAi() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        byte[] pdf = TestPdfs.withEmbeddedXml(TestPdfs.resource("/einvoices/zugferd-cii.xml"), "factur-x.xml");
        UUID id = upload(employee, pdf("zugferd.pdf", pdf));
        awaitStatus(id, InvoiceStatus.EXTRACTED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.source").value("E_INVOICE"))
                .andExpect(jsonPath("$.eInvoiceFormat").value("ZUGFeRD / Factur-X"))
                .andExpect(jsonPath("$.invoiceNumber").value("ZF-4711"));
        verify(extractor, never()).extract(anyString());
    }

    @Test
    void xmlThatIsNoInvoiceFailsWithAClearReason() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        UUID id = upload(employee, xml("order.xml", "/einvoices/not-an-invoice.xml"));
        awaitStatus(id, InvoiceStatus.FAILED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.errorMessage").value(
                        "XML file is not a supported e-invoice (XRechnung UBL/CII, ZUGFeRD/Factur-X)"));
    }

    @Test
    void scannedPdfIsReadAsImages() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        when(extractor.extractFromImages(anyList())).thenReturn(sampleExtraction());

        UUID id = upload(employee, pdf("scan.pdf", TestPdfs.withLines()));
        awaitStatus(id, InvoiceStatus.EXTRACTED);

        mvc.perform(as(employee, get("/api/invoices/{id}", id)))
                .andExpect(jsonPath("$.source").value("AI_VISION"))
                .andExpect(jsonPath("$.supplierName").value("Muster Buerobedarf GmbH"));
        verify(extractor, never()).extract(anyString());
    }

    @Test
    void otherFileTypesAreRejected() throws Exception {
        String employee = loginAs(Role.EMPLOYEE);
        mvc.perform(as(employee, multipart("/api/invoices").file(
                        new MockMultipartFile("file", "bild.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Only PDF invoices and e-invoices (XRechnung / ZUGFeRD XML) are supported"));
    }

    private static MockMultipartFile xml(String name, String resource) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_XML_VALUE, TestPdfs.resource(resource));
    }

    private UUID upload(String token, MockMultipartFile file) throws Exception {
        String body = mvc.perform(as(token, multipart("/api/invoices").file(file)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }
}
