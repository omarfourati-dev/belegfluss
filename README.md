# Belegfluss – AI-powered invoice inbox

[![CI & Deploy](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml/badge.svg)](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml)

Small businesses receive supplier invoices as PDFs and still type supplier, invoice number,
amounts and IBAN into their accounting by hand. **Belegfluss** ("document flow") takes the PDF,
lets an LLM extract the fields as structured data and prepares them for review and approval.

Built with **Java 21** and **Spring Boot 3**, using **Spring AI** for structured LLM output
and **Spring Security** (JWT) for role-based access and a four-eyes approval workflow.

**Live demo:** https://belegfluss.omarfourati.de – read-only login `demo@belegfluss.app` / `demo-belegfluss`

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

## Roles and approval workflow

```
RECEIVED ──AI──► EXTRACTED ──approve──► APPROVED ──book──► BOOKED
             └─► FAILED     └─reject──► REJECTED
```

| Role | Can do | Inherits |
|---|---|---|
| `VIEWER` | read invoices and history | – |
| `EMPLOYEE` | upload invoices | VIEWER |
| `APPROVER` | approve / reject (never their own upload) | EMPLOYEE |
| `ACCOUNTANT` | book approved invoices | APPROVER |
| `ADMIN` | create users | ACCOUNTANT |

- **Four-eyes principle:** the person who uploaded an invoice cannot approve it.
- **Audit trail:** every step is stored with actor and timestamp (`GET /api/invoices/{id}/history`).
- Stateless JWT (HS256) issued by the API itself, validated as an OAuth2 resource server;
  role hierarchy via Spring Security `RoleHierarchy`, checks with `@PreAuthorize`.
- Passwords hashed with BCrypt; failed logins take the same time for known and unknown e-mails.

## Automatic checks

After the extraction every invoice is checked. Warnings never block, but approving an invoice
with warnings requires a comment (`422` otherwise).

| Warning | What it catches |
|---|---|
| `DUPLICATE` | same supplier and invoice number again – prevents paying twice |
| `IBAN_CHANGED` | supplier used another IBAN before – classic payment fraud pattern |
| `INVALID_IBAN` | ISO 13616 mod-97 check digits are wrong |
| `VAT_MISMATCH` | net + VAT ≠ gross (1 cent tolerance) |
| `UNUSUAL_VAT_RATE` | VAT rate is not 0 %, 7 % or 19 % |
| `MISSING_FIELDS` | supplier, invoice number or gross amount could not be read |

## Frontend

Vue 3 + TypeScript + Vite + Tailwind, served by Spring Boot from the same origin (no CORS):
login, dashboard with key figures, drag & drop upload, live status via Server-Sent Events,
detail view with PDF preview, warnings, audit trail and role-dependent actions, CSV export
and user management.

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to :8080
```

## Tech stack

| Area | Technology |
|---|---|
| Core | Java 21 (records, virtual threads), Spring Boot 3.5 |
| AI | Spring AI 1.0, structured output, any OpenAI-compatible API (OpenAI, Google Gemini) |
| Persistence | Spring Data JPA, PostgreSQL 17, Flyway |
| PDF | Apache PDFBox 3 |
| Security | Spring Security, OAuth2 resource server (JWT), BCrypt, role hierarchy |
| API | REST, OpenAPI / Swagger UI, RFC 9457 problem details |
| Operations | Actuator, Prometheus metrics, Docker (layered jar, non-root), GitHub Actions |
| Frontend | Vue 3, TypeScript, Vite, Tailwind CSS, Server-Sent Events |
| Tests | JUnit 5, AssertJ, Mockito, Testcontainers (real PostgreSQL), Awaitility, Vitest, Vue Test Utils |

## API

| Method | Path | Role |
|---|---|---|
| `POST` | `/api/auth/login` | public – returns a JWT |
| `GET` | `/api/auth/me` | any |
| `POST` | `/api/invoices` | EMPLOYEE – upload a PDF (`multipart/form-data`, field `file`, max 10 MB) |
| `GET` | `/api/invoices`, `/api/invoices/{id}` | VIEWER |
| `GET` | `/api/invoices/{id}/history` | VIEWER – audit trail |
| `GET` | `/api/invoices/events` | VIEWER – live status (Server-Sent Events) |
| `GET` | `/api/invoices/export.csv` | ACCOUNTANT – DATEV-style CSV |
| `GET` | `/api/invoices/{id}/document` | VIEWER – original PDF |
| `POST` | `/api/invoices/{id}/approve` | APPROVER |
| `POST` | `/api/invoices/{id}/reject` | APPROVER – with reason |
| `POST` | `/api/invoices/{id}/book` | ACCOUNTANT |
| `GET`, `POST` | `/api/users` | ADMIN |

Interactive docs: `http://localhost:8080/swagger-ui.html` – log in, then click **Authorize** and paste the token.

## Run locally

Requirements: Java 21, Docker.

```bash
cp .env.example .env              # add your API key (OpenAI or Gemini)
docker compose up -d postgres     # start PostgreSQL
./mvnw spring-boot:run            # start the API on :8080
```

Or run everything in containers:

```bash
docker compose --profile app up --build
```

Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `.env` to create the first admin on start. Then:

```bash
TOKEN=$(curl -s -H "Content-Type: application/json"   -d '{"email":"admin@example.com","password":"your-admin-password"}'   http://localhost:8080/api/auth/login | jq -r .accessToken)

curl -H "Authorization: Bearer $TOKEN" -F "file=@rechnung.pdf" http://localhost:8080/api/invoices
```

## Tests

```bash
./mvnw verify                 # backend: unit + integration tests
cd frontend && npm test       # frontend: Vitest
```

The integration tests start a real PostgreSQL with Testcontainers, use the real security
configuration (login, JWT, roles) and mock only the LLM.

## Roadmap

- [x] Upload, background extraction, REST API, Docker, CI
- [x] Login with Spring Security (JWT) and roles: employee, approver, accounting
- [x] Approval workflow (four-eyes principle) and audit trail
- [x] Checks: duplicate invoices, VAT plausibility, IBAN check digits, changed IBAN
- [x] Live status updates via Server-Sent Events
- [x] Vue 3 + TypeScript frontend with dashboard
- [x] CSV / DATEV-style export
- [x] Live demo deployment
- [ ] Ideas: OCR for scanned PDFs, e-invoices (XRechnung / ZUGFeRD), e-mail inbox import

## Author

**Omar Fourati** – Full-Stack Developer · [omarfourati.de](https://omarfourati.de) · [GitHub](https://github.com/omarfourati-dev)
