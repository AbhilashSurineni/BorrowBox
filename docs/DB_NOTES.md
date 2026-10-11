# Database notes (MySQL 8)

The API moved from PostgreSQL + Prisma to MySQL 8 + Flyway + Hibernate. This file records what changed at the database level and why.

## Schema conventions

| Concern | PostgreSQL version | MySQL version |
|---|---|---|
| Primary keys | `uuid` | `CHAR(36)`, generated in Java (`UUID.randomUUID()`) |
| Enums | native enum types | `VARCHAR` + `CHECK (... IN (...))`, mapped with `@Enumerated(STRING)` |
| Timestamps | `timestamptz` | `DATETIME(6)` stored in UTC (`hibernate.jdbc.time_zone=UTC`, JDBC `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`) |
| Calendar dates | `date` | `DATE` (`LocalDate`) |
| Ratings | `numeric(3,2)` | `DECIMAL(3,2)` |
| Audit metadata, settings values | `jsonb` | `JSON` |
| Long text | `text` | `TEXT` |
| Booleans | `boolean` | `TINYINT(1)` |
| Engine / charset | — | InnoDB, `utf8mb4`, `utf8mb4_0900_ai_ci` |

**Migrations**
- Migrations live in `apps/api/src/main/resources/db/migration`:
  - `V1__schema.sql`: all tables, indexes, unique keys and CHECKs.
  - `V2__loan_overlap_triggers.sql`: the overlap triggers.
- Hibernate runs with `ddl-auto: validate`, so an entity that drifts from the migrations fails at startup.

**Indexes and constraints**
- All PostgreSQL indexes and unique constraints are kept, for example `reviews (loan_id, reviewer_id)`, `users.email` and `categories.name`.
- So are the CHECKs `end_date >= start_date` and `rating BETWEEN 1 AND 5`. MySQL enforces CHECK constraints from 8.0.16.

**Renamed columns.** `condition` and `key` are reserved words in MySQL, so the columns are renamed:
- `items.condition` → `items.item_condition`
- `settings.key` → `settings.setting_key`

The JSON field names are unchanged.

**New columns**
- `loans.version` is an optimistic-lock column. A status write on a loan that changed concurrently fails with 409 `INVALID_TRANSITION`, which replaces the TS `UPDATE ... WHERE status = <from>` guard.
- `refresh_tokens.token_hash` stores the SHA-256 of each refresh token; the raw token is never stored.

**Ordering.** PostgreSQL sorted enums in declaration order. The admin report queue therefore orders by an explicit `CASE status ...` to keep OPEN → UNDER_REVIEW → RESOLVED → DISMISSED.

## Double-booking protection (Business Rule 3)

PostgreSQL enforced Rule 3 with an exclusion constraint:

```sql
EXCLUDE USING gist (item_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
  WHERE (status IN ('APPROVED','ACTIVE','OVERDUE'))
```

MySQL has no exclusion constraints, so three layers replace it.

1. **Row lock on the item.** Every loan create, approve and extension runs in one `@Transactional` method. Item deletion and admin takedown do the same. The method first locks the item row with `SELECT ... FROM items WHERE id = ? FOR UPDATE` (Spring Data `@Lock(PESSIMISTIC_WRITE)`). Concurrent writers for the same item therefore queue up, and each sees the previous one's committed result.
   - Request creation and approval also lock the borrower's `users` row, so the Rule 4 limits (3 active, 5 pending) cannot be raced. This replaces the PostgreSQL advisory lock.
   - Locks are always taken item first, then user, so they cannot deadlock.
2. **Application overlap check, inside that transaction.** The method checks for an APPROVED/ACTIVE/OVERDUE loan of the same item where `start_date <= :end AND end_date >= :start AND id <> :self`. An OVERDUE loan blocks until it is returned, so its end is treated as `GREATEST(end_date, today)`. An overlap throws `DATES_UNAVAILABLE` (HTTP 409).
3. **Triggers as a backstop.** `loans_no_overlap_insert` and `loans_no_overlap_update` are BEFORE INSERT and BEFORE UPDATE triggers. They run `SIGNAL SQLSTATE '45000'` (message `loans_no_overlap`) when the new row would overlap an APPROVED/ACTIVE/OVERDUE row of the same item. `GlobalExceptionHandler` maps that error to 409 `DATES_UNAVAILABLE` too.
   - This covers anything that bypasses the service, such as manual SQL or a future bug.
   - The trigger's check is a plain read, so on its own it is not race-proof. That is why layer 1 is the primary guard.

**Isolation level.** Connections use **READ COMMITTED** (`spring.datasource.hikari.transaction-isolation`).
- Under MySQL's default REPEATABLE READ, a transaction that waited for the item lock would keep reading the snapshot taken before it waited. It could then approve a request that the first transaction had just auto-rejected.
- READ COMMITTED gives each statement a fresh view, matching the PostgreSQL behaviour the logic was written for.

The test `KeyFlowsTest#concurrentApprovals` fires two simultaneous approvals for overlapping requests and asserts:
- exactly one returns 200 and the other 409
- the loans end up APPROVED and REJECTED
- a direct SQL insert of an overlapping APPROVED loan is rejected by the trigger.

### Trigger privileges

When binary logging is on, which is the MySQL 8 default, creating a trigger requires `SUPER` or `log_bin_trust_function_creators=1`. The app user is not SUPER, so the setting is applied as follows:

| Environment | How it is set |
|---|---|
| docker-compose and Testcontainers | MySQL starts with `--log-bin-trust-function-creators=1` |
| CI (GitHub service container) | a step runs `SET GLOBAL log_bin_trust_function_creators = 1` before `mvn verify` |
| Managed MySQL (RDS, Cloud SQL, …) | set `log_bin_trust_function_creators = 1` in the parameter group, or run the migrations as an admin user |

## Scheduled jobs: cross-instance locking

- The PostgreSQL version used `pg_try_advisory_xact_lock`. Each job now takes `GET_LOCK('borrowbox:job:<name>', 0)` on a dedicated connection, runs its body in its own transaction, and then calls `RELEASE_LOCK`. If another instance holds the lock, the run is skipped.
- Daily jobs insert `(job, run_key=date)` into `job_runs` with `INSERT IGNORE`, so they run at most once per day.
- Scheduled notifications use a unique `dedupe_key`, inserted with `INSERT ... ON DUPLICATE KEY UPDATE id = id`. Retries therefore never notify twice.

## Full-text search

- PostgreSQL used a GIN `to_tsvector` index. MySQL uses `FULLTEXT KEY items_search_idx (name, description)` and queries it with `MATCH(name, description) AGAINST (:q IN NATURAL LANGUAGE MODE)`.
- When a keyword is given, results are ranked by relevance: first for the default "newest" sort, and as a tie-breaker for the "rating" and "soonest available" sorts.
- InnoDB ignores words shorter than `innodb_ft_min_token_size` (3) and stop words.
- "Soonest available" and the derived item status are computed in the same SQL statement through a `LATERAL` derived table, which needs MySQL 8.0.14 or later. There is no N+1.
