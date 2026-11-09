# FlowDesk backend

Spring Boot 4 / Java 21 / PostgreSQL. Session-cookie auth, request workflow with an immutable audit trail.

## Run

**Prerequisites:** Java 21 or newer (the build targets 21), PostgreSQL (developed against 16). Maven is not needed: use the bundled `./mvnw`.

1. **Create the database** (Flyway creates the tables, not the database itself):
   ```bash
   createdb flowdesk            # or: psql -c 'CREATE DATABASE flowdesk;'
   ```
2. **Add your credentials.** Copy the template and edit it (the real file is git-ignored):
   ```bash
   cp local.properties.example local.properties
   ```
   Set `spring.datasource.url`, `.username` and `.password`. The `SPRING_DATASOURCE_*` environment variables work too.
3. **Start the API** (listens on **http://localhost:8081**, set by `server.port`):
   ```bash
   ./mvnw spring-boot:run
   ```
   On first start Flyway applies `V1__init_schema.sql` and `V2__external_operations.sql`.
4. **Seed data.** With `app.seed.enabled=true` (the default in `application.properties`), the app creates these users
   on startup if they don't exist yet. Set it to `false` in production.

   | Role | Email | Password |
   |---|---|---|
   | Admin | `admin@flowdesk.test` | `Admin123!` |
   | Requester | `alice@flowdesk.test` | `Password123!` |
   | Requester | `bob@flowdesk.test` | `Password123!` |

   To start from scratch: `dropdb flowdesk && createdb flowdesk`, then start the API again.
5. **Run the frontend** (`flowdesk-fe`) next: `npm install && npm run dev`, then open http://localhost:5173.
   See that README. CORS allows only that origin (`app.cors.allowed-origin`).

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

## Fulfilling a request (external provider)

Moving a request to `FULFILLED` calls an external provider (`fulfillment/FulfillmentClient`). The call is made
safe to retry; see [Engineering decisions](#engineering-decisions). Today the provider is
`SimulatedFulfillmentClient`, an in-memory stand-in. To use a real one, implement `FulfillmentClient`
(send the idempotency key to the provider; throw `FulfillmentTimeoutException` on timeouts and
`FulfillmentRejectedException` on a definitive refusal) and remove `@Component` from the simulated class.

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

## Engineering decisions

- **Retry-safe fulfilling (the main one).** If the provider completes the work but the response is lost
  (timeout), a naive retry would process it twice. Instead:
  1. *Record intent first.* In one transaction, validate and write a row in `external_operations` with a stable
     idempotency key (`fulfill-request-{id}`).
  2. *Call the provider outside any transaction*, sending that key. A database transaction must not span a
     network call: it would hold a connection open and could not undo the provider's work anyway.
  3. *A timeout means "unknown", not "failed".* Ask the provider (`lookup(key)`) what happened. If it still can't
     confirm, return `504 FULFILLMENT_OUTCOME_UNKNOWN`, leave the request `APPROVED` and keep the operation `PENDING`.
  4. *Retry reuses the same key*, so the provider returns the original result instead of repeating the work.
  5. *Finalize in one transaction:* mark the operation `SUCCEEDED`, set the request `FULFILLED`, write the audit row.
  An operation already `SUCCEEDED` locally skips the provider entirely, and `FULFILLED` is a final state, so a
  repeat gets `409`.
- **The workflow is one table** (`workflow/WorkflowRules`). The service, the `availableActions` sent to the UI and the
  tests all read it, so the rules cannot drift apart. The backend is the source of truth for permissions; the UI only
  renders what it is told.
- **Status change and audit row commit together**, and `request_activity` is append-only (entity `@Immutable`, plus
  database triggers that reject UPDATE/DELETE/TRUNCATE).
- **Optimistic locking (`@Version`)** turns two admins editing the same request into a `409`, not a silent overwrite.
- **Session-cookie auth with CSRF on**, instead of tokens in browser storage: the session cookie is HttpOnly, so
  page scripts cannot read it.
- **One error shape.** `GlobalExceptionHandler` turns every failure into the same `ApiResponse` body. Unexpected
  errors return a generic message and log the details server-side.
- **Hidden requests are `404`, not `403`**, so a requester cannot learn that another user's request exists.

## Known limitations

- **The fulfillment provider is simulated.** It lives in memory, never times out and forgets everything on restart.
- **Stuck operations are only resolved by a manual retry.** An operation left `PENDING` after an unknown outcome has
  no background job to reconcile it.
- **Two simultaneous first clicks on Fulfill:** the provider is still called only once (same key), but the loser of
  the race gets a generic `409 DATA_INTEGRITY_VIOLATION` rather than a tailored message.
- **Sessions are in memory** (default Tomcat), so they are lost on restart and not shared between instances.
  Use Spring Session (JDBC or Redis) before running more than one instance.
- **No login rate limiting or lockout.**
- **Tests run on H2, not PostgreSQL.** The migrations, the append-only triggers and the database `CHECK` constraints
  are not exercised by the test suite.
- **Test gaps:** login/logout, list filtering and paging, concurrent updates and the exception-handler mappings
  have no automated tests.
- **Seed users use well-known passwords.** Disable seeding (`app.seed.enabled=false`) outside local development.
