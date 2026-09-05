# StockFlow

A minimal Inventory & Invoicing system for a small distribution business: staff members track
products with stock on hand, raise invoices for customers, and stock moves automatically —
down when an invoice is issued, back when it is cancelled.

Take-home test submission for **Full-Stack Developer (Java + Angular)**.

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security (JWT), Flyway, PostgreSQL, Swagger/OpenAPI
- **Frontend:** Angular 22 (standalone components, signals), Tailwind CSS v4
- **Tests:** 11 integration tests against real PostgreSQL via Testcontainers

---

## Quick start

### Prerequisites

- JDK 21+ (`java -version`)
- Node.js 22+ and npm (`node -v`)
- Docker (for PostgreSQL and the test suite)

### 1. Start PostgreSQL

```bash
docker run -d --name stockflow-db \
  -e POSTGRES_DB=stockflow -e POSTGRES_USER=stockflow -e POSTGRES_PASSWORD=stockflow \
  -p 5433:5432 postgres:17-alpine
```

> Note: the container is mapped to host port **5433** because many machines (especially macOS with
> Homebrew) already run a local Postgres on 5432. Adjust `DB_URL` below if you use a different port.

### 2. Configure environment

```bash
cp .env.example .env   # then edit if needed
```

The backend reads env vars directly (see `.env.example` for the full list and how to generate a
`JWT_SECRET`). If you skip this step, the defaults in `application.yml` work for local development.

### 3. Run the backend

```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5433/stockflow SEED_ENABLED=true ./mvnw spring-boot:run
```

- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html

The first run applies Flyway migrations and (with `SEED_ENABLED=true`) creates the demo user and
8 sample products.

### 4. Run the frontend

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200 — the dev server proxies `/api/*` to the backend on port 8080.

### Demo credentials

| Email | Password |
|---|---|
| `demo@stockflow.local` | `password123` |

You can also register your own account from the UI.

### Run tests

```bash
cd backend
./mvnw test
```

This spins up an isolated PostgreSQL via Testcontainers (requires Docker) and runs 11 integration
tests, including all five mandated scenarios: wrong-password rejection, 401 on unauthenticated
access, overstock rejection, stock decrement on issue, and stock restore on cancel.

---

## API overview

Interactive docs at `/swagger-ui.html`. Summary:

| Method & path | Description | Auth |
|---|---|---|
| `POST /api/auth/register` | Register (email + password ≥ 8 chars) | — |
| `POST /api/auth/login` | Login, returns JWT | — |
| `POST /api/auth/logout` | Client-side logout contract (204) | — |
| `GET /api/products` | List, `?search=&page=&size=&sortBy=&sortDir=` | Bearer |
| `GET/POST/PUT/DELETE /api/products/{id}` | Product CRUD | Bearer |
| `GET /api/invoices` | List, `?status=&page=&size=` | Bearer |
| `GET /api/invoices/{id}` | Invoice with items and totals | Bearer |
| `POST /api/invoices` | Create DRAFT invoice (1+ items) | Bearer |
| `PUT /api/invoices/{id}` | Edit items/customer — DRAFT only | Bearer |
| `POST /api/invoices/{id}/issue` | DRAFT → ISSUED, atomically decrements stock | Bearer |
| `POST /api/invoices/{id}/pay` | ISSUED → PAID | Bearer |
| `POST /api/invoices/{id}/cancel` | DRAFT/ISSUED → CANCELLED (restores stock if ISSUED) | Bearer |

Errors always use the same JSON shape:

```json
{ "status": 409, "error": "Conflict", "message": "Insufficient stock for ...",
  "fieldErrors": {"sku": "SKU already exists"}, "timestamp": "..." }
```

---

## Design decisions & why

- **Spring Boot 4 + PostgreSQL + Flyway** — mainstream, well-understood stack; schema is versioned
  and reproducible from a clean clone; Hibernate runs with `ddl-auto=validate` so Flyway owns the schema.
- **Money as `BigDecimal` + `NUMERIC(12,2)`** — never `double`/`float`; tax rate (`TAX_RATE`, default
  `0.11`) is env-configurable and applied server-side. Totals are **always** recalculated on the server;
  client totals are display-only.
- **Stateless JWT (HS256) over httpOnly cookies** — simpler for a split-origin dev setup; the secret
  comes from `JWT_SECRET`. Trade-off: no server-side revocation, so logout is client-side token
  discard (see limitations). Login failures always return the same generic message to prevent
  user enumeration; password hashing is bcrypt (strength 12) with server-side policy (≥ 8 chars).
- **Per-user workspace via query scoping** — every repository query filters by `user_id`; foreign
  ids resolve as `404` (not `403`) so resource existence is not leaked to other users.
- **Stock guard at the row level** — issuing runs `UPDATE ... SET qty = qty - :n WHERE id = ? AND
  qty >= :n` per line inside one transaction; a zero-row update (insufficient stock) throws and
  rolls back everything, so either all lines decrement or none do. Additionally, create/edit of a
  draft rejects aggregated quantities above available stock with a `409` naming the product.
- **Hard delete blocked for referenced products** — deleting a product referenced by any invoice
  line returns `409`. Chosen over soft-delete: invoices keep their own snapshot, so nothing needs
  the product row to disappear gracefully.
- **Snapshot pricing** — `productName` and `unitPrice` are copied onto invoice items at
  create/edit time; later product changes never alter historical invoices.
- **Invoice numbers** — `INV-<year>-<sequence>` from a Postgres sequence, globally unique.
- **Angular signals + Tailwind v4** — small, modern frontend; plain functional UI as the test allows.

## Trade-offs & known limitations

- **Logout is client-side** — with stateless JWT there is no server-side token revocation. With
  more time: short-lived access tokens + refresh-token rotation, or a denylist.
- **No refresh tokens** — a single 8-hour access token; acceptable for an internal tool demo.
- **`PUT` replaces the whole item list** on draft edit (no line-level PATCH) — simpler and safe.
- **No optimistic-lock conflict UX** — products carry a `@Version`, but concurrent edits surface
  as a generic error rather than a merge prompt.
- **Testing is API-level only** — no unit tests for trivial getters, no E2E browser tests.
- **Single currency, single locale** — amounts are IDR-formatted in the UI only.

## What I would do with one more week

1. Refresh-token rotation + server-side revocation list.
2. Stock-movement ledger (append-only audit of every increment/decrement with reason).
3. Optimistic-locking retry UX for concurrent invoice issuance.
4. Playwright E2E covering the register → invoice → issue → cancel journey.
5. GitHub Actions CI (build + test on every push) and a deployed demo on Fly.io/Vercel.
6. Invoice PDF export / print view.

## AI usage

This project was built with the help of an AI coding agent (Hermes Agent driving an LLM):
scaffolding, boilerplate (entities/DTOs/repositories), test authoring and this README were drafted
with AI assistance; all code was reviewed, verified against a live database, and iterated on
(see git history — several bugs were caught by live verification and fixed, e.g. the
edit-after-issue guard and the stock guard on draft creation). Every line is understood by the
author and defensible in a walkthrough.

## Hours spent

Roughly 6–7 hours of focused work (including setup, live verification of every business rule,
and this documentation).
