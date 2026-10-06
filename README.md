# Belegfluss – AI-powered invoice inbox

[![CI & Deploy](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml/badge.svg)](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml)

Small businesses receive supplier invoices as PDFs and still type supplier, invoice number,
amounts and IBAN into their accounting by hand. **Belegfluss** ("document flow") takes the PDF,
lets an LLM extract the fields as structured data and prepares them for review and approval.

Built with **Java 21** and **Spring Boot 3**, using **Spring AI** for structured LLM output.

## How it works

```
POST /api/invoices (PDF)
        │
        ▼
  store PDF in PostgreSQL ── status RECEIVED ──► 202 Accepted
        │  (after commit)
        ▼
  @Async listener on a virtual thread
        │
        ├─ PDFBox: extract text layer
        ├─ Spring AI: text ──► ExtractedInvoice (record, JSON schema)
        ▼
  status EXTRACTED  (or FAILED with reason)
```

- The upload returns immediately. Extraction runs in the background after the transaction
  has committed (`@TransactionalEventListener` + `@Async`).
- The slow LLM call runs **outside** any database transaction, so no connection is blocked.
- Spring AI maps the model answer directly to a Java record. The field descriptions in
  `ExtractedInvoice` are sent as part of the JSON schema and act as extraction instructions.
- The extractor sits behind an `InvoiceExtractor` interface, so the provider can be swapped
  and tests do not need an API key.

## Tech stack

| Area | Technology |
|---|---|
| Core | Java 21 (records, virtual threads), Spring Boot 3.5 |
| AI | Spring AI 1.0 (OpenAI chat model, structured output) |
| Persistence | Spring Data JPA, PostgreSQL 17, Flyway |
| PDF | Apache PDFBox 3 |
| API | REST, OpenAPI / Swagger UI, RFC 9457 problem details |
| Operations | Actuator, Prometheus metrics, Docker (layered jar, non-root), GitHub Actions |
| Tests | JUnit 5, AssertJ, Mockito, Testcontainers (real PostgreSQL), Awaitility |

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/invoices` | Upload a PDF (`multipart/form-data`, field `file`, max 10 MB) |
| `GET` | `/api/invoices` | List invoices, newest first |
| `GET` | `/api/invoices/{id}` | Invoice with extracted fields and status |
| `GET` | `/api/invoices/{id}/document` | Original PDF |

Interactive docs: `http://localhost:8080/swagger-ui.html`

## Run locally

Requirements: Java 21, Docker.

```bash
cp .env.example .env              # add your OPENAI_API_KEY
docker compose up -d postgres     # start PostgreSQL
./mvnw spring-boot:run            # start the API on :8080
```

Or run everything in containers:

```bash
docker compose --profile app up --build
```

Upload an invoice:

```bash
curl -F "file=@rechnung.pdf" http://localhost:8080/api/invoices
```

## Tests

```bash
./mvnw verify
```

The integration tests start a real PostgreSQL with Testcontainers and mock only the LLM.

## Roadmap

- [x] Upload, background extraction, REST API, Docker, CI
- [ ] Login with Spring Security (JWT) and roles: employee, approver, accounting
- [ ] Approval workflow and audit trail
- [ ] Checks: duplicate invoices, VAT plausibility, unknown IBAN
- [ ] Live status updates via WebSocket
- [ ] Vue 3 + TypeScript frontend with dashboard
- [ ] CSV / DATEV-style export
- [ ] Live demo deployment

## Author

**Omar Fourati** – Full-Stack Developer · [omarfourati.de](https://omarfourati.de) · [GitHub](https://github.com/omarfourati-dev)
