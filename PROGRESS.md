# BorrowBox — Progress

Source of truth for requirements: `docs/BorrowBox_Project_Documentation.pdf`. Working conventions are in `CLAUDE.md`; database specifics are in `docs/DB_NOTES.md`.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation: repo, configs, schema and migrations, env, middleware, CI, docker-compose | ✅ Done |
| 2 | Full Section 10 API, LoanService state machine, Section 11 jobs, seed, Section 15 key tests | ✅ Done |
| 3 | React frontend (`apps/web`): all 14 Section 12 screens | ✅ Done |
| 3.5 | **Backend migrated to Java 21 + Spring Boot 3.5 + MySQL 8** (same API contract) | ✅ Done |
| 3.6 | **Frontend migrated to plain JavaScript + Axios** (same screens, same contract) | ✅ Done |
| 4+ | Playwright E2E, OpenAPI document, deployment pipeline, Cloudinary/S3 driver | ⏳ Not started |

Last verification run (all passing):
- `./mvnw -B verify` in `apps/api` (12 tests: the 8 Section 15 key cases on Testcontainers MySQL 8, plus state-machine and rating-math unit tests)
- `npm run lint` and `npm run build` for the web app (71 JS/JSX files, 0 lint errors or warnings)
- The web app run in headless Chrome against the Java API on :8080, with every screen checked and the Axios refresh queue verified
- `docker build` of the API image

## Decisions log

| Date | Decision |
|---|---|
| 2026-10-11 | Initial backend: Node/Express/TypeScript, Prisma, PostgreSQL, with a btree_gist exclusion constraint for Rule 3. |
| 2026-10-11 | Frontend: React, Vite, Tailwind, React Router and React Query; the shared Zod package is consumed from source. |
| 2026-10-11 | **Backend migrated to Spring Boot + MySQL; the exclusion constraint was replaced by a row lock plus a trigger.** Java 21 / Spring Boot 3.5 / Spring Data JPA / Spring Security / Flyway / MySQL 8 now replace Express / Prisma / PostgreSQL. The API contract is unchanged. MySQL has no exclusion constraints, so Rule 3 is now enforced in three layers: a `SELECT … FOR UPDATE` on the item row, an in-transaction overlap check (409 `DATES_UNAVAILABLE`), and BEFORE INSERT/UPDATE triggers that `SIGNAL '45000'` (also mapped to 409). Connections use READ COMMITTED so the work done after the lock sees fresh data. Details are in `docs/DB_NOTES.md`. The TS backend (`legacy/api-ts`) and `packages/shared` were deleted after the parity check. The web app kept its own copy of the contract in `apps/web/src/shared`. |
| 2026-10-11 | **Deploy target: Render (API + MySQL) and Vercel (web).** `render.yaml` defines MySQL 8 as a private service with a disk, using `deploy/mysql` (utf8mb4, `log_bin_trust_function_creators=1`, and a datadir sub-folder because the disk contains lost+found), plus the API with an uploads disk. The API also accepts `DB_HOST`, `DB_PORT` and `DB_NAME` as an alternative to `DB_URL`. Vercel rewrites `/api/*` and `/uploads/*` to Render (`apps/web/vercel.json`), so the browser only uses the Vercel origin; this avoids cross-site cookie blocking in Safari/iOS. This setup was verified locally with a simulated proxy. |
| 2026-10-11 | **Frontend migrated to JavaScript.** All `.ts` and `.tsx` files became `.js` and `.jsx`: types were stripped mechanically with Sucrase, preserving layout, then cleaned up by hand. TypeScript, `tsconfig`, `@types/*` and Zod were removed. A single Axios instance (`src/lib/api.js`) now handles all API calls; it uses `withCredentials`, a Bearer interceptor and a single shared refresh on 401 with retry, and normalises errors to `{ code, message }`. Each feature has its own API module. `src/shared` was replaced by `src/lib/constants.js` and the plain-JS `src/lib/validators.js`. Tailwind, React Router, React Query and Recharts are kept. The API's default port moved from 4000 to 8080 to match the frontend default `VITE_API_URL=http://localhost:8080/api/v1`; the Vite proxy was dropped and the browser calls the API cross-origin with CORS and credentials. |

## Repository layout

```
apps/api                 Spring Boot API (Maven, ./mvnw)
  src/main/java/com/borrowbox
    auth/                register, verify, login, rotating refresh cookie, password reset
    users/               profile, public profile, reviews list, account deletion
    items/               browse/search (FULLTEXT + LATERAL SQL), CRUD with images, availability, categories
    loans/               LoanService (the only writer of loan status), LoanStateMachine, LoanViews, controller
    reviews/             reviews and rating math
    notifications/       in-app notifications (+ email after commit)
    reports/             user/item reports
    admin/               dashboard stats, moderation, takedowns, reminders, reports queue, settings, announcements, audit log
    jobs/                @Scheduled jobs, JobRunner (MySQL GET_LOCK + job_runs idempotency)
    common/              config, security (JWT filter, Bucket4j, CORS, headers), errors, web helpers,
                         storage (local disk + Cloudinary/S3 stubs, ImageIO re-encode), mail, settings, audit, time, seed
  src/main/resources     application.yml (+ dev/test/prod), db/migration (Flyway V1 schema, V2 triggers)
  src/test/java          IntegrationTestBase (Testcontainers / TEST_DB_URL), KeyFlowsTest, LoanStateMachineTest
  Dockerfile             multi-stage, non-root runtime
apps/web                 React frontend: plain JavaScript (JSX) + Axios, Vite, Tailwind, React Router, React Query, Recharts
docker-compose.yml       MySQL 8 (utf8mb4, healthcheck, volume, trigger flag) + API
.github/workflows/ci.yml JDK 21 + MySQL service + mvn verify; Node 22 lint + build
```

## API contract (unchanged)

All Section 10 endpoints plus the earlier additions (`POST /auth/resend-verification`, `GET|POST /admin/announcements`, `GET /admin/audit-logs`, `GET /health`, static `/uploads/*`), served under `/api/v1`.

Everything else stays as it was:
- camelCase fields, the same enum strings and the same error body, codes and HTTP statuses
- pagination as `{ data, meta: { page, limit, total, totalPages } }`, with `unreadCount` added for notifications
- `YYYY-MM-DD` dates and ISO-8601 UTC instants
- the httpOnly refresh cookie `bb_refresh` with path `/api/v1/auth`

### Parity check (TS → Java), 2026-10-11

The legacy TS API (PostgreSQL) and the new Java API (MySQL) were run side by side on the same demo data and compared:
- **Routes:** 56 of 56 identical (method and path). There were no routes on only one side.
- **Error codes:** the `ErrorCode` sets are identical (36 codes).
- **Response shapes:** 22 GET endpoints, covering every list and detail endpoint for members and admins, were compared by recursive JSON key structure and value types. There were 0 differences.
- **Error scenarios:** 16 cases returned the same HTTP status and code:
  - past dates, own item, too long, malformed body
  - wrong role on approve and on extension
  - unauthenticated, member on an admin route
  - unknown loan, non-UUID id
  - bad credentials, wrong email domain, missing refresh cookie
  - unknown route, reporting yourself, unknown settings key
- **Frontend:** the unchanged React app was rendered in headless Chrome against the Java API on every member and admin page. All rendered with no console errors and no horizontal scroll, and session restore through the refresh cookie worked.
- **Gaps found and fixed during the check:**
  - An invalid enum value used to abort JSON binding and report only that one field. Request enums are now validated as strings, so all field errors come back together, as with Zod.
  - A text block dropped the space after `WHERE` in the search SQL.

**Known, intentional differences** (none affect the frontend):
- **WebP uploads** are stored as PNG (with transparency) or JPEG, because ImageIO cannot write WebP. JPEG and PNG keep their format. Metadata is still stripped and size is still capped at 1600px.
- **Keyword search ranks by relevance.** It is the primary order for the default "newest" sort and a tie-breaker otherwise. This uses MySQL FULLTEXT natural-language mode, so words under 3 characters and stop words are ignored.
- **Rate-limit responses are the same** (429 `RATE_LIMITED`), but the IETF `RateLimit-*` response headers are no longer sent.
- **Page size cap stays at 100**, the same as the TS API. The migration brief mentioned a 50-per-page cap, but the TS code (the source of truth) used 100.

## Business rules (Section 6): where they live

| # | Rule | Enforcement |
|---|---|---|
| 1 | No borrowing own item | `LoanService.create` → 403 `OWN_ITEM` |
| 2 | Max duration | `create` → 422 `EXCEEDS_MAX_DURATION`. Days are inclusive and must be ≤ the item's `max_loan_days`. Extensions can add up to `max_loan_days`. |
| 3 | No overlapping approved loans | Item row lock, then the overlap check (409 `DATES_UNAVAILABLE`), with the `loans_no_overlap_*` triggers as a backstop. See `docs/DB_NOTES.md`. |
| 4 | ≤ 3 active and ≤ 5 pending (configurable) | `create` (429 `TOO_MANY_ACTIVE_LOANS` / `TOO_MANY_PENDING`), re-checked on approve. The borrower's row is locked. |
| 5 | An overdue borrower can't request | `create` → 403 `OVERDUE_LOAN_EXISTS`. It also blocks approval of their pending requests. |
| 6 | One review per party per loan | Unique `(loan_id, reviewer_id)` plus a pre-check → 409 `REVIEW_EXISTS` |
| 7 | ≥ 3 upheld reports → auto-suspend | `AdminService.updateReport` → `applyAutoSuspension`. It counts RESOLVED reports by `target_user_id`, suspends the user, revokes their tokens, writes an audit entry with a null actor, and notifies the user. |
| 8 | One extension; owner approves; no collision | `extension_status` goes NONE → REQUESTED → APPROVED/DECLINED. The item is locked and the overlap is checked before the end date moves. |

The loan state machine is `LoanStateMachine.TRANSITIONS`: the Section 7 table plus `OVERDUE --EXTEND_OVERDUE--> ACTIVE`, with the allowed actors (OWNER, BORROWER, SYSTEM) for each transition. A disallowed transition returns 409 `INVALID_TRANSITION` with the message `Cannot <event> a loan that is <STATUS>`.

## Backend decisions and assumptions (Java)

**Layers**
- Controllers handle HTTP only.
- Services hold the rules and own the transactions.
- Spring Data repositories, plus `ItemSearchRepository` and `AdminStatsRepository` (JDBC), are used only from services.
- Request and response DTOs are records; entities are never serialised.

**Entities**
- Entities are flat persistence structs with public fields and foreign keys as ids, with no lazy associations.
- Presenters (`LoanViews`, `ItemViews`, `UserViews`) batch-load the related rows, so lists do not trigger N+1 queries.

**Validation**
- Requests are validated with Jakarta Bean Validation and mapped to 400 `VALIDATION_ERROR`, with `details: { formErrors, fieldErrors }` in Zod's flatten shape.
- User text is sanitised in the DTO compact constructors before validation: tags and control characters are stripped and the text is trimmed.
- Endpoints that accept both multipart and JSON (item create/update, profile update) bind the form map through `RequestBinder`.
- For PATCH requests, a blank `depositNote` or `phone` clears the field and an absent one leaves it unchanged.

**Authentication**
- `JwtAuthFilter` reads the Bearer token and loads the user on every request, so suspensions apply immediately.
- Authentication is enforced per handler by the `AuthUser` argument resolver: required by default, `@Verified`, or `@OptionalAuth`. Unknown routes therefore still return 404 before auth, matching the TS app.
- ADMIN routes also carry `@PreAuthorize("hasRole('ADMIN')")`.

**Tokens**
- The access JWT (HS256) lasts 15 minutes.
- The refresh token is a JWT stored only as its SHA-256 hash in `refresh_tokens`. It rotates on every refresh.
- Re-using a rotated token revokes the whole family. The refresh method uses `@Transactional(noRollbackFor = BusinessException.class)` so that revocation commits even though the request fails.
- HMAC keys are the SHA-256 of `JWT_SECRET` and `JWT_REFRESH_SECRET`, so any secret of 16 characters or more works.
- BCrypt strength is 12 (4 in the test profile).

**Security**
- Security headers:
  - CSP `default-src 'none'; img-src 'self'; frame-ancestors 'none'`
  - `X-Frame-Options: DENY`, `nosniff` and `Referrer-Policy: no-referrer`
  - `Cross-Origin-Resource-Policy: cross-origin`, so images load from the web origin
  - HSTS, on HTTPS only
- CORS allows only `CORS_ORIGIN`, which defaults to `APP_URL`, with credentials.
- Bucket4j applies the same rate-limit windows as before, in memory per instance:

  | Endpoints | Limit |
  |---|---|
  | login | 10 / 15 min per IP |
  | register and resend-verification (shared bucket) | 10 / h per IP |
  | forgot and reset password (shared bucket) | 5 / h per IP |
  | loan requests | 30 / h per user |

- JSON bodies are capped at 100 kB (413).

**Uploads**
- `ImageService` checks the declared MIME type and size: 5 MB each, 5 per item, and only the multipart field names `images` or `avatar`.
- It then detects the real format with ImageIO, using TwelveMonkeys for WebP, applies the EXIF orientation (metadata-extractor), resizes to at most 1600px and re-encodes. Re-encoding strips EXIF and GPS data.
- `StorageService` has a local-disk driver served from `/uploads/**`. The Cloudinary and S3 drivers are stubs selected with `STORAGE_DRIVER`.

**Mail.** `MailService` uses JavaMailSender in smtp mode, logs to the console in log mode (the dev default, and the fallback when no SMTP host is set), and keeps messages in memory in memory mode (tests). Templates and triggers are identical to the TS version, and mail is sent after commit.

**Jobs**
- Schedules (`@Scheduled`, `app.timezone`):

  | Job | Schedule |
  |---|---|
  | expire pending | every 15 min |
  | mark overdue | hourly at :00 |
  | due reminders | hourly at :05, acting once a day at or after `reminder_hour` (default 08:00) |
  | start-date nudge | hourly at :10, with the same daily gate |
  | auto-complete | daily at 03:20 |

- Each job runs under MySQL `GET_LOCK` on a dedicated connection. Daily jobs record a row in `job_runs`. Notifications are deduped by `dedupe_key`.
- Every status change goes through `LoanService` (`expirePending`, `markOverdue`, `autoComplete`).

**Seeding.** `DataSeeder` (every profile, idempotent) creates the 8 categories, the default settings and the admin from `ADMIN_EMAIL`/`ADMIN_PASSWORD`. `DemoDataSeeder` (dev profile only) creates the same demo users, items, loans in every state, reviews, a notification and a report as before.

**Profiles**

| Profile | Behaviour |
|---|---|
| `dev` (default) | console mail, default secrets, demo data |
| `test` | BCrypt strength 4, no cron, no rate limits, in-memory mail |
| `prod` | secure cookies; secrets required from the environment |

**Tests**
- `IntegrationTestBase` starts `mysql:8.0` with Testcontainers, or uses `TEST_DB_URL` (as in CI), and truncates and re-seeds before each test.
- `KeyFlowsTest` covers the 8 Section 15 cases. Case 8 uses an `ExecutorService` and a latch to fire two approvals at once.
- `LoanStateMachineTest` covers the transition table, illegal transitions, actor rules and rating rounding (HALF_UP to 2 decimals).

**Rule details carried over from the TS version unchanged**
- Two-step handover: the owner marks it, the borrower confirms, from the start date onwards.
- Two-step return: either party marks it and the other confirms. Only the owner's condition note is kept.
- Cancel: from PENDING only the borrower can cancel; from APPROVED either party can.
- Approving auto-rejects overlapping PENDING requests; so does approving an extension, for the added days.
- A loan reaches COMPLETED when both parties have reviewed, or 7 days after return.
- Expiry also covers PENDING requests whose start date has passed.
- Account deletion anonymises the user (it is blocked while they have active loans).
- Item and user deletes are soft deletes.
- Admin takedown rejects pending requests and cancels approved loans.
- Contact details (email, and phone unless the preference is EMAIL) go only to the counterparty once the loan is APPROVED or later. Admin loan views include both parties' contacts.

## Frontend (`apps/web`)

**Stack.** React 18 in plain JavaScript (JSX), HTML and CSS, built with Vite 6. Axios 1 handles all HTTP. Tailwind 3, React Router 6, TanStack Query 5 and Recharts 2 (admin only, code-split) are also used. There is no TypeScript. PropTypes are declared on the shared UI and domain components. ESLint uses the JS recommended rules, `eslint-plugin-react` and `react-hooks`.

**API layer**
- `src/lib/api.js` holds the single Axios instance:
  - `baseURL` comes from `VITE_API_URL` (default `http://localhost:8080/api/v1`), with `withCredentials: true`.
  - The request interceptor attaches `Bearer <access token>`.
  - The response interceptor handles a 401 with one shared `POST /auth/refresh`, which concurrent requests queue behind, then retries the request. If the refresh fails, it logs out.
  - Errors are normalised to `ApiError { status, code, message, details }`.
  - Array query params are comma-joined.
- Feature API modules are `src/features/{auth,items,loans,notifications,users,admin}/api.js`. React Query hooks in `features/*/hooks.js` call them. There are no `fetch` calls anywhere.
- Session restore on page load and the proactive refresh one minute before the access token expires work as before.
- Verified in the browser: 3 concurrent requests with an expired token triggered exactly 1 refresh and all succeeded; a failed refresh cleared the session; multipart item creation works; `OWN_ITEM` and `VALIDATION_ERROR` arrive normalised.

**Validation and constants.** `src/lib/validators.js` covers required fields, length, email and campus domain (`VITE_ALLOWED_EMAIL_DOMAIN`), password rules, phone, date range and rating 1–5, plus the form-level `validateRegister` and `validateItem`. `src/lib/constants.js` holds the enum strings and `DEFAULT_SETTINGS`. These replace the Zod `src/shared` copy.

**Screens (Section 12):** all 14 are done, in JavaScript, and were checked against the Java API.
- [x] 1 Landing (`/`)
- [x] 2 Register / Login (`/register`, `/login`, plus `/forgot-password`, `/reset-password`, `/verify-email`)
- [x] 3 Home / Browse (`/browse`, URL-synced filters, mobile filter drawer, pagination)
- [x] 4 Item Detail (`/items/:id`: gallery, owner card, availability calendar, request button, reviews, report link)
- [x] 5 Request Modal (date range picker, message, summary and rules, friendly error per code)
- [x] 6 Add / Edit Item (`/items/new`, `/items/:id/edit`, image uploader with previews)
- [x] 7 My Items (`/my/items`: Listed and Paused tabs, request badges)
- [x] 8 My Loans (`/my/loans`: Borrowing and Lending tabs, status chips, action buttons)
- [x] 9 Loan Detail (`/loans/:id`: timeline, contact after approval, handover, return and extension)
- [x] 10 Notifications (`/notifications`, plus the unread bell)
- [x] 11 Profile (`/profile` own with edit form; `/users/:id` public)
- [x] 12 Admin Dashboard (`/admin`: stat cards, Recharts, quick links)
- [x] 13 Admin Users, Items, Loans and Reports (tables, filters, confirm dialogs)
- [x] 14 Admin Settings (`/admin/settings`: limits, categories, announcements)

**Key behaviours (unchanged)**
- Route guards: guest-only, member and admin.
- Every API error code has friendly copy.
- Loan action buttons come from the API's `availableActions`, and destructive actions use confirm dialogs.
- Mobile-first layout with WCAG AA contrast.
- Status chips use the Section 12 colours.
- The Recharts dashboard uses a validated palette, and each chart has a table view.

**Development.** The browser calls the API directly at `VITE_API_URL`. The API must allow the web origin through `APP_URL`/`CORS_ORIGIN` with credentials; the default is `http://localhost:5173`. `localhost:5173` and `localhost:8080` count as the same site, so the `SameSite=Lax` refresh cookie is sent. In production, serve both over HTTPS. If the web app and API are on different sites, set `COOKIE_SAMESITE=None`.

## How to run

```bash
docker compose up -d mysql                 # MySQL 8 (utf8mb4, trigger flag), user/pass borrowbox/borrowbox
cd apps/api && ./mvnw spring-boot:run      # API :8080, dev profile: console emails + demo data
npm install && npm run dev                 # web :5173 (VITE_API_URL defaults to http://localhost:8080/api/v1)
# or the whole backend in containers:
docker compose up --build                  # mysql + api
```

Logins: `admin@campus.edu / Admin12345`, `alice|bob|carol|dan@campus.edu / Demo12345` (dev profile).

Checks:

```bash
cd apps/api && ./mvnw -B verify            # needs Docker for Testcontainers, or TEST_DB_URL
npm run lint && npm run build
```

## Next steps

1. Playwright E2E for the critical flow (register → list → request → approve → handover → return → review).
2. OpenAPI spec (springdoc) generated from the controllers, and keep `apps/web/src/lib/constants.js` in sync with it.
3. Implement the Cloudinary/S3 storage driver. Add a data-export endpoint (Section 13).
4. Deployment pipeline: build and push the API image, run migrations on release, and host the frontend.
