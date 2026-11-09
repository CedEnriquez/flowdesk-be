# FlowDesk backend

Spring Boot 4 / Java 21 / PostgreSQL. Session-cookie auth, request workflow with an immutable audit trail.

## Run

1. Put your DB credentials in `local.properties` in the project root (git-ignored):
   `spring.datasource.url`, `.username`, `.password` (e.g. `jdbc:postgresql://localhost:5432/flowdesk`). Flyway creates the schema on startup.
2. `./mvnw spring-boot:run`
3. Demo users (seeded when `app.seed.enabled=true`; turn it off in production):
   `admin@flowdesk.test / Admin123!`, `alice@flowdesk.test` and `bob@flowdesk.test / Password123!`

Tests: `./mvnw test` (H2, no database needed). See [Testing](#testing).

## Architecture

`controller` (HTTP only) -> `service` (rules, transactions) -> `repository` -> `entity`.
`dto` holds request/response records; **every** endpoint returns `ApiResponse<T>`
(`{timestamp, status, code, message, data, fieldErrors[]}`), errors included, via `GlobalExceptionHandler`.
Security-filter errors (401/403/CSRF) use the same shape.

The lifecycle lives in one table: `workflow/WorkflowRules`. The service, `availableActions` and the tests all read it.

| From | To | Who | Extra |
|---|---|---|---|
| SUBMITTED | IN_REVIEW | admin | |
| SUBMITTED | CANCELLED | owner | |
| IN_REVIEW | APPROVED | admin | |
| IN_REVIEW | REJECTED | admin | comment required |
| IN_REVIEW | CANCELLED | owner | |
| APPROVED | FULFILLED | admin | |

(The six statuses and these transitions were inferred from the route names; adjust the table if the RD differs.)

Check order in `RequestService.transition`: visibility (404) -> transition exists (409) -> role/ownership (403) -> comment (400).
Status update and audit row share one `@Transactional`; `@Version` turns concurrent edits into 409.

## Audit immutability

`request_activity` is `@Immutable`, has no setters or routes, and a Postgres trigger rejects UPDATE/DELETE/TRUNCATE.
Note: the RD's `CHECK (new_status <> 'REJECTED' OR length(trim(comment)) > 0)` lets a NULL comment through
(NULL > 0 is unknown, which CHECK accepts), so the migration adds `comment IS NOT NULL AND`.

## Security notes

- CSRF is **enabled** (`XSRF-TOKEN` cookie, send it back as `X-XSRF-TOKEN`). Any request, e.g. `GET /api/auth/me`, sets the cookie.
- CORS: one explicit origin (`app.cors.allowed-origin`), credentials on, explicit methods/headers.
- Session id rotates on login; cookie is HttpOnly, SameSite=Lax. **Production:** set `server.servlet.session.cookie.secure=true` and serve over HTTPS (HSTS is sent automatically on HTTPS responses).
- Login errors are generic. **Not implemented, recommended for production:** login rate limiting / lockout (e.g. Bucket4j or at the gateway).
- Request size: header/form limits in properties; every DTO field has `@Size`.
- Only JPA parameter binding; `password_hash` is never returned.
- **Production, React app:** serve it with `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'`, plus `nosniff` and `Referrer-Policy`, configured on the host/web server.

## Testing

```bash
./mvnw test                                                  # all tests (20)
./mvnw test -Dtest=WorkflowRulesTest                         # one class
./mvnw test -Dtest=RequestFlowIntegrationTest#invalidTransition_409   # one test method
```

Tests run against an in-memory H2 database (`src/test/resources/application-test.properties`), so no Postgres
or running server is needed. Per-class results are written to `target/surefire-reports/`.

| Test class | Tests | What it covers |
|---|---|---|
| `workflow/WorkflowRulesTest` | 5 | The lifecycle table: final states have no exits; reject needs an admin and a comment; cancel is owner-only; review cannot be skipped; available actions depend on role and ownership |
| `controller/RequestFlowIntegrationTest` | 10 | The real API through MockMvc: uniform `401` shape; `400` with field errors for a blank title; `403` for a requester approving; `400` for reject without a comment; `409` for an invalid transition; `404` when a requester opens another user's request; full submitted-to-fulfilled run writes a complete audit trail; owner can cancel but admin-only actions stay blocked; missing CSRF token rejected; security headers present |
| `fulfillment/FulfillmentIdempotencyIntegrationTest` | 5 | Retry-safe fulfilling, using a fake provider that dedupes by idempotency key: the normal path calls the provider once and stores its reference; a lost response after success is resolved by lookup without repeating the work; an unknown outcome returns `504` and leaves the request `APPROVED`, and the retry succeeds with the provider having run once; fulfilling again after success returns `409` and never calls the provider; a provider refusal returns `502`, changes nothing, and can be retried |
