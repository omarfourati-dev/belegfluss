package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.auth.CurrentUser;
import de.omarfourati.belegfluss.invoice.InvalidUploadException;
import de.omarfourati.belegfluss.invoice.Invoice;
import de.omarfourati.belegfluss.invoice.InvoiceService;
import de.omarfourati.belegfluss.invoice.InvoiceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    private final InvoiceStatusStream statusStream;

    public InvoiceController(InvoiceService service, InvoiceStatusStream statusStream) {
        this.service = service;
        this.statusStream = statusStream;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Operation(summary = "Upload a PDF invoice (EMPLOYEE)", description = "Returns 202, extraction runs in the background.")
    public ResponseEntity<InvoiceResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @AuthenticationPrincipal Jwt jwt) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidUploadException("File is empty");
        }
        Invoice invoice = service.receive(file.getOriginalFilename(), file.getBytes(), CurrentUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(invoice.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(InvoiceResponse.from(invoice));
    }

    @GetMapping
    @Operation(summary = "List all invoices, newest first")
    public List<InvoiceResponse> list() {
        return service.findAll().stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live status updates (Server-Sent Events)",
            description = "Sends an `invoice` event {invoiceId, status} whenever an invoice changes.")
    public SseEmitter events() {
        return statusStream.subscribe();
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    @Operation(summary = "Accounting export as CSV (ACCOUNTANT)",
            description = "DATEV-style CSV: semicolons, decimal comma. Default: approved and booked invoices.")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "APPROVED,BOOKED") List<InvoiceStatus> status) {
        byte[] csv = InvoiceCsvExport.toCsv(service.findForExport(status)).getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("belegfluss-export.csv").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one invoice with its extracted fields")
    public InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(service.get(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Audit trail: who did what and when")
    public List<InvoiceEventResponse> history(@PathVariable UUID id) {
        return service.history(id).stream().map(InvoiceEventResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Approve an extracted invoice (APPROVER, not the uploader)",
            description = "If the automatic checks found warnings, a comment is required.")
    public InvoiceResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        String comment = request == null ? null : request.comment();
        return InvoiceResponse.from(service.approve(id, CurrentUser.from(jwt), comment));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Reject an extracted invoice with a reason (APPROVER)")
    public InvoiceResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return InvoiceResponse.from(service.reject(id, CurrentUser.from(jwt), request.reason()));
    }

    @PostMapping("/{id}/book")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    @Operation(summary = "Book an approved invoice (ACCOUNTANT)")
    public InvoiceResponse book(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return InvoiceResponse.from(service.book(id, CurrentUser.from(jwt)));
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
                .body(service.document(id));
    }

    public record DecisionRequest(@Size(max = 500) String comment) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
