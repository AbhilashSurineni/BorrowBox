# BorrowBox — working notes for Claude

Campus borrowing and lending platform. The requirements are in `docs/BorrowBox_Project_Documentation.pdf`; status and decisions are in `PROGRESS.md`; database specifics are in `docs/DB_NOTES.md`.

## Stack

- **API** (`apps/api`): Java 21, Spring Boot 3.5 (Web, Data JPA/Hibernate, Security, Validation, Mail), MySQL 8, Flyway, Maven (`./mvnw`). JWT via jjwt, rate limits via Bucket4j, tests with JUnit 5, MockMvc and Testcontainers.
- **Web** (`apps/web`): React 18 in **plain JavaScript (JSX)**, HTML and CSS. It is built with Vite and uses Axios for all HTTP calls, plus Tailwind, React Router, React Query and Recharts. Do not use TypeScript. It is an npm workspace, run from the repo root.
- The API contract is fixed. It lives under `/api/v1`:
  - camelCase JSON fields and enum strings exactly as the frontend expects
  - errors as `{ "error": { "code", "message", "details"? } }`
  - paging as `{ data, meta: { page, limit, total, totalPages } }`
  - ISO-8601 dates (`YYYY-MM-DD`) and instants

  Do not change these without updating `apps/web`.

## Commands

```bash
docker compose up -d mysql                         # MySQL 8 with the flags the triggers need
cd apps/api && ./mvnw spring-boot:run              # API on :8080 (dev profile: console emails, demo data)
cd apps/api && ./mvnw -B verify                    # compile + tests (Testcontainers; or set TEST_DB_URL)
npm run dev                                        # web on :5173, calls VITE_API_URL (default http://localhost:8080/api/v1)
npm run lint && npm run build                      # frontend checks (also: npm run preview)
```

## Backend conventions (non-negotiable)

- **Controller = HTTP only.** Controllers parse and validate the request, then call one service method. They never touch repositories or entities directly.
- **Service = business rules.** Services own transactions (`@Transactional`), authorization (ownership and role checks), notifications and audit logging.
- **Repositories are used only from services** (and from `@Repository` query classes such as `ItemSearchRepository`). Use Spring Data JPA, and native or JDBC queries where SQL is clearer.
- **Every loan status change goes through `LoanService`.** It applies the `LoanStateMachine` transition table (from, event, to, allowed actors) and nothing else assigns `Loan.status`. That includes jobs, cascades and admin actions: call a `LoanService` method.
- **Rule 3 (no double booking).** Lock the item row (`ItemRepository.lockById`, `PESSIMISTIC_WRITE`), then check overlaps, in the same transaction. The `loans_no_overlap_*` triggers are only a backstop. Take locks in the order item → user.
- **DTOs only on the wire.** Use request and response records. Entities are internal persistence structs and must never be serialised.
- **Validation and errors**
  - Validate request DTOs with Jakarta Bean Validation. Sanitise user text in the record's compact constructor (`Sanitizer.clean`).
  - Validation failures are 400 `VALIDATION_ERROR` with `details: { formErrors, fieldErrors }`.
  - For expected failures, throw `BusinessException(status, ErrorCode, message)`. `GlobalExceptionHandler` maps everything else.
- **Authentication**
  - Add an `AuthUser` handler parameter to require login. Mark it `@Verified` to also require a verified email, or `@OptionalAuth` when login is optional. Put it first in the parameter list so auth errors win over validation errors.
  - ADMIN controllers use `@PreAuthorize("hasRole('ADMIN')")`.
- **Side effects after commit.** Emails go through `AfterCommit.run(...)`, usually via `NotificationService.notify`.
- **Time.** Use `CampusClock` (`app.timezone`) for "today". Store instants in UTC.
- **Schema changes** go in a new Flyway migration (`V3__...sql`); never edit an applied one. Hibernate runs with `ddl-auto=validate`.
- **Settings and seed data.** Defaults are in `Settings.DEFAULTS`. Startup seeding (`DataSeeder`, and `DemoDataSeeder` in the dev profile only) is idempotent.

## Frontend conventions

- **Plain JavaScript only:** `.js` and `.jsx` files, with no TypeScript, `tsconfig` or `@types/*`. Shared UI and domain components declare `PropTypes` where it is cheap; the ESLint `react/prop-types` rule is off.
- **All HTTP goes through the single Axios instance** in `src/lib/api.js`. Feature API modules (`src/features/<feature>/api.js`) call its `apiGet`, `apiPost`, `apiPatch` and `apiDelete` helpers, and React Query hooks (`src/features/<feature>/hooks.js`) call those modules. Never use `fetch` or create another client.
  - Requests use `withCredentials: true` (the refresh token is an httpOnly cookie) and send `Authorization: Bearer <access token>` from memory.
  - A 401 runs one shared `POST /auth/refresh` and then retries the request. If the refresh fails, the session is cleared (logout).
  - Errors are normalised to `ApiError { status, code, message, details }`.
  - Array query params are sent comma-joined (`status=A,B`), which is what the API expects.
- **Client-side validation** uses `src/lib/validators.js` (required, length, email and campus domain, password rules, date range, rating 1–5, plus `validateRegister` and `validateItem`). The server validates everything again; show its `details.fieldErrors` with `fieldErrors()` from `lib/errorMessages.js`.
- **Contract constants** (enum strings, `DEFAULT_SETTINGS`) live in `src/lib/constants.js`, and error copy keyed by error code lives in `src/lib/errorMessages.js`. When the API adds an enum value or `ErrorCode`, update these by hand, since nothing enforces it at build time.
- Import with the `@/` alias for `src/` (configured in `vite.config.js` and `jsconfig.json`).
