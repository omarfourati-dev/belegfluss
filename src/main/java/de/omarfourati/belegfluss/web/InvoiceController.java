package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.invoice.Invoice;
import de.omarfourati.belegfluss.invoice.InvalidUploadException;
import de.omarfourati.belegfluss.invoice.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF invoice", description = "Returns 202, extraction runs in the background.")
    public ResponseEntity<InvoiceResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidUploadException("File is empty");
        }
        Invoice invoice = service.receive(file.getOriginalFilename(), file.getBytes());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(invoice.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(InvoiceResponse.from(invoice));
    }

    @GetMapping
    @Operation(summary = "List all invoices, newest first")
    public List<InvoiceResponse> list() {
        return service.findAll().stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one invoice with its extracted fields")
    public InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(service.get(id));
    }

    @GetMapping(value = "/{id}/document", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the original PDF")
    public ResponseEntity<byte[]> document(@PathVariable UUID id) {
        Invoice invoice = service.get(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(invoice.getOriginalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(invoice.getPdfContent());
    }
}
