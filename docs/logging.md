# Logging Plan (Agent Prompt Reference)

This document defines *what* we want to log, *where*, *how*, and *what not to log*, so an AI agent (or a human) can implement logging changes consistently.

## Goals

- Make the system operable on a 24/7 host (laptop): diagnose failures quickly.
- Provide clear visibility into:
  - Blizzard sync job runs (start/end, counts, duration, failures)
  - Admin database changes (who changed what)
  - Unexpected errors (one clear log per failure)
- Avoid leaking secrets (Blizzard credentials, OAuth tokens, JWTs).
- Keep logs low-noise for public GET endpoints.

## Non-goals

- Full distributed tracing.
- Storing raw Blizzard AH payloads in logs.
- Logging every HTTP request/response body.

## Logging standards

### Logger usage
- Use SLF4J (`org.slf4j.Logger` / `LoggerFactory`).
- Do not use `System.out.println` or `printStackTrace()`.

### Levels
- `INFO`: normal lifecycle events + summaries (startup, sync run summary, admin CRUD summary)
- `WARN`: recoverable issues (skipped items, invalid input, retries, 4xx from admin misuse)
- `ERROR`: failures that prevented an operation (sync run failed, DB write failed)
- `DEBUG`: detailed internal state (only for development; never default in prod)

### Message style (structured key-value)
Prefer stable, greppable messages:

- `event=<name> key=value key=value ...`

Examples:
- `event=blizzard_sync_started runId=...`
- `event=blizzard_sync_finished runId=... durationMs=... auctions=... matchedItems=... updatedItems=... unknownItems=...`
- `event=admin_item_updated actor=<sub> itemId=...`

### Correlation / run identifiers
- For scheduler runs: generate a `runId` (UUID) at the start of a sync cycle and include it in all logs for that cycle.
- For HTTP requests (optional step): add a request/correlation id (MDC) so errors can be traced across logs.

## Sensitive data policy (must follow)

Never log:
- Blizzard client secret (`BLIZZARD_CLIENT_SECRET`)
- Blizzard OAuth access tokens
- JWT bearer tokens (`Authorization` header contents)
- Database passwords / full JDBC URLs with credentials

Be careful with:
- `ingredientsJson`: do not log full content; log length and recipe id instead.

## What to log by component

### Public endpoints
- `GET /health`, `GET /items`, `GET /recipes`
- Default: no per-request `INFO` logs.
- Log only:
  - unhandled exceptions as `ERROR`
  - optionally slow requests (future)

### Admin endpoints
Endpoints:
- `/admin/items` (POST/PUT/DELETE)
- `/admin/recipes` (POST/PUT/DELETE)

Log at `INFO` for successful mutations:
- actor identity (JWT `sub` when auth is implemented; until then: `actor=anonymous`)
- operation (`created|updated|deleted`)
- entity id(s)

Log at `WARN` for expected client errors:
- validation failure
- unknown referenced ids (e.g., outputItemId)

Log at `ERROR` for unexpected exceptions:
- DB failures

### Blizzard sync (core scheduler)
For every run, produce:

1) Start log (`INFO`)
- `event=blizzard_sync_started runId=...`

2) External call timing (`INFO` or `DEBUG`)
- `event=blizzard_api_call_finished runId=... status=... durationMs=...`

3) Summary log (`INFO`) — most important
- `event=blizzard_sync_finished runId=... durationMs=... auctionsTotal=... itemsMatched=... itemsUpdated=... unknownItems=... algorithm=WEIGHTED_MEDIAN`

4) Failure log (`ERROR`)
- `event=blizzard_sync_failed runId=... error=...`

Do not log raw payloads; if needed, log only:
- payload size (bytes)
- counts

## Configuration

### Recommended defaults (prod)
- `logging.level.root=INFO`
- `logging.level.com.crafting=INFO`
- `logging.level.org.springframework=WARN`
- `logging.level.org.hibernate.SQL=OFF`

### Dev overrides
- enable `DEBUG` only for `com.crafting` when actively debugging.

## Implementation checklist (agent tasks)

This is an ordered list of changes an agent can implement safely.

1) Replace `System.out` logging in Blizzard components
- Files to check:
  - `src/main/java/com/crafting/blizz/**`
- Replace with SLF4J and add run summaries.

2) Add scheduler run wrapper logging
- When the scheduler framework exists, ensure it logs start/finish/failure and includes a `runId`.

3) Add admin mutation audit logs
- Files:
  - `src/main/java/com/crafting/controller/admin/AdminItemController.java`
  - `src/main/java/com/crafting/controller/admin/AdminRecipeController.java`
- Log `INFO` on success and `WARN` for invalid requests.

4) Add centralized exception logging
- Add a `@RestControllerAdvice` that logs unhandled exceptions once and returns a consistent JSON error.

5) (Optional) Add request correlation id
- Add a servlet filter that:
  - reads `X-Request-Id` header if provided, otherwise generates one
  - stores it in MDC and echoes it back in response header

6) Document log fields
- Keep this doc updated with actual event names and fields once implemented.

## Acceptance criteria

- No secrets appear in logs.
- Each Blizzard sync run produces exactly one start and one finish (or fail) log.
- Admin CUD actions produce one `INFO` log with entity ids.
- Unhandled exceptions are logged once with stack trace.
