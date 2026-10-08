# Belegfluss – AI-powered invoice inbox

[![CI & Deploy](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml/badge.svg)](https://github.com/omarfourati-dev/belegfluss/actions/workflows/deploy.yml)

Small businesses receive supplier invoices as PDFs and still type supplier, invoice number,
amounts and IBAN into their accounting by hand. **Belegfluss** ("document flow") takes the invoice –
a digital PDF, a scan or a German e-invoice (XRechnung, ZUGFeRD) – extracts the fields as structured
data and takes it through checks, a four-eyes approval and booking.

Built with **Java 21** and **Spring Boot 3**, using **Spring AI** for structured LLM output
and **Spring Security** (JWT) for role-based access and a four-eyes approval workflow.

**Live demo:** https://belegfluss.omarfourati.de (landing page) · app: https://belegfluss.omarfourati.de/app/ – read-only login `demo@belegfluss.app` / `demo-belegfluss`

## How it works

```
POST /api/invoices (PDF or XML)
        │
        ▼
  store document in PostgreSQL ── status RECEIVED ──► 202 Accepted
        │  (after commit)
        ▼
  @Async listener on a virtual thread, cheapest exact source first:
        │
        ├─ e-invoice XML (XRechnung UBL/CII) or ZUGFeRD PDF attachment ──► parsed exactly, no AI
        ├─ PDF with text layer ──► Spring AI on the text ──► ExtractedInvoice record
        └─ scanned PDF ──► pages rendered to PNG ──► Spring AI vision ──► ExtractedInvoice record
        ▼
  automatic checks ──► status EXTRACTED with warnings (or FAILED with reason)
```

### E-invoices (XRechnung, ZUGFeRD / Factur-X)

Since 2025 German businesses must be able to receive e-invoices. Belegfluss reads both EN 16931
syntaxes – **UBL 2.1** (XRechnung) and **UN/CEFACT CII** (XRechnung CII, ZUGFeRD, Factur-X) – either
as an uploaded XML file or embedded in a PDF. These invoices are taken over exactly and cost no AI
tokens. The XML parser is hardened against XXE (no DTDs, no external entities).

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

- **Four-eyes principle:** neither the uploader nor the person who last corrected the fields can
  approve – nobody can change the IBAN and release the payment alone.
- **Manual correction** of extracted fields (also to rescue a failed extraction); checks run again
  and the audit trail lists which fields changed.
- **Audit trail:** every step is stored with actor and timestamp (`GET /api/invoices/{id}/history`).
- Stateless JWT (HS256) issued by the API itself, validated as an OAuth2 resource server;
  role hierarchy via Spring Security `RoleHierarchy`, checks with `@PreAuthorize`.
- Passwords hashed with BCrypt; failed logins take the same time for known and unknown e-mails.
- Login throttling: 5 failed attempts per account and IP (20 per IP) pause logins for 15 minutes (`429`).
- Users change their own password; the public demo account is locked against changes.

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

Two pages are built: a static **landing page** at `/` that explains the project and works without
JavaScript, and the **app** at `/app/` (noindex). The landing page carries what search engines and
AI assistants need: meta and Open Graph tags, JSON-LD (`SoftwareApplication`, `FAQPage`, `Person`),
`robots.txt`, `sitemap.xml` and an [`llms.txt`](frontend/public/llms.txt) summary. Old links to
`/#/...` are redirected to `/app/#/...`.

The app is an **installable PWA** (manifest, icons, [service worker](frontend/public/app/sw.js) with scope `/app/`).
The service worker caches only the app shell – the app page, the hashed files under `/assets/` and the icons –
and never anything under `/api`, so no invoices, PDFs or tokens end up in the device cache and the live
SSE stream is left alone. Offline the app still opens and says it has no connection. Each build stamps the
service worker ([`scripts/stamp-sw.mjs`](frontend/scripts/stamp-sw.mjs)), so after a deploy the app offers
"Neu laden".

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
| `POST` | `/api/invoices` | EMPLOYEE – upload a PDF or e-invoice XML (`multipart/form-data`, field `file`, max 10 MB) |
| `PATCH` | `/api/invoices/{id}` | EMPLOYEE – correct extracted fields |
| `DELETE` | `/api/invoices/{id}` | ADMIN – delete with document and history (not booked invoices) |
| `POST` | `/api/auth/password` | any – change own password |
| `GET` | `/api/invoices`, `/api/invoices/{id}` | VIEWER |
| `GET` | `/api/invoices/{id}/history` | VIEWER – audit trail |
| `GET` | `/api/invoices/events` | VIEWER – live status (Server-Sent Events) |
| `GET` | `/api/invoices/export.csv` | ACCOUNTANT – DATEV-style CSV |
| `GET` | `/api/invoices/{id}/document` | VIEWER – original PDF or XML |
| `POST` | `/api/invoices/{id}/approve` | APPROVER |
| `POST` | `/api/invoices/{id}/reject` | APPROVER – with reason |
| `POST` | `/api/invoices/{id}/book` | ACCOUNTANT |
| `GET`, `POST` | `/api/users` | ADMIN – list and create users |
| `PATCH` | `/api/users/{id}` | ADMIN – disable / re-enable a user (`{"enabled": false}`) |

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
./mvnw verify                 # backend: unit + integration tests (Testcontainers)
cd frontend && npm test       # frontend: Vitest
cd frontend && npm run e2e    # end-to-end: Playwright against a running stack
```

The Playwright test runs the whole business process in a real browser – an employee uploads an
XRechnung, an approver releases it, accounting books it – and checks the four-eyes rule. Because
e-invoices need no AI, CI runs it against the real Docker stack without any API key.

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
- [x] E-invoices: XRechnung (UBL/CII) and ZUGFeRD / Factur-X, without AI
- [x] Scanned PDFs via vision model
- [x] Manual correction with audit trail, password change, login throttling
- [x] Playwright end-to-end tests in CI
- [x] Landing page with SEO (structured data, sitemap) and GEO (`llms.txt`, AI crawlers allowed)
- [x] Admins delete invoices that are not booked (GoBD: booked invoices stay)
- [x] Business metrics for Prometheus: invoices per status, extractions by source and outcome, logins
- [x] Access on request: no open sign-up; admins create, disable and re-enable accounts (tokens of disabled users stop working at once)
- [ ] Ideas: e-mail inbox import, DATEV-API export, multi-tenant setup

## Author

**Omar Fourati** – Full-Stack Developer · [omarfourati.de](https://omarfourati.de) · [GitHub](https://github.com/omarfourati-dev)
