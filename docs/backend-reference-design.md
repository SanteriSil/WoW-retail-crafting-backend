# WoW Retail Crafting Backend – Reference Design

**Audience:** project partners + AI agents

**Purpose:** Provide a single, stable reference for what this backend must do, how it is structured, what exists today, and the step-by-step implementation plan.

---

## 1. Problem Statement

The backend maintains a database of:
- **Items** (commodity items) and their **current market value**
- **Item price history** (time series of that market value)
- **Recipes** created by project admins (inputs → output item, output quantity)

The frontend (hosted separately) uses **public GET endpoints** to fetch items/recipes/history and performs all profitability calculations client-side.

The backend is also responsible for **periodic ingestion of Auction House commodity data from Blizzard APIs** and condensing it into a single representative value per item.

Admin users can create/update/delete items and recipes via **authenticated admin endpoints**.

A later phase adds **bulk snapshot upload** (same JSON schema as Blizzard), produced by an in-game addon and uploaded by admins. This design prepares for that, but the Blizzard scheduler is core.

---

## 2. Current Repo State (Anchors)

### 2.1 Existing data model
- Item entity: `src/main/java/com/crafting/model/Item.java`
  - `currentPrice` (copper) and `currentPriceRecordedAt`
- Recipe entity: `src/main/java/com/crafting/model/Recipe.java`
  - `ingredientsJson` (raw JSON string)
  - `outputQuantity` (Float, supports 1..N; default 1.0)
- Price history entity: `src/main/java/com/crafting/model/ItemPriceHistory.java`

Schema migrations:
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/main/resources/db/migration/V2__seed_professions.sql`

### 2.2 Existing public API
- Items list (public): `src/main/java/com/crafting/controller/ItemController.java` (`GET /items`)

### 2.3 Existing Blizzard integration (prototype)
- `src/main/java/com/crafting/blizz/AHDataFetcher.java`
  - Fetches OAuth token + calls EU commodities endpoint
  - Parses auctions into `(unit_price, quantity)`
  - Filters by placeholder DB item ids (hardcoded)
  - Runs on startup (`@PostConstruct`) and does not persist

### 2.4 Current security
- `src/main/java/com/crafting/config/SecurityConfig.java`
  - CSRF disabled; H2 frames allowed
  - Currently permits all requests

---

## 3. Target Architecture

### 3.1 Data flow overview
1. Scheduler triggers a Blizzard commodities fetch (EU).
2. Parse `auctions[]` into price observations per item: `(unitPrice, quantity)`.
3. For each item that exists in our DB:
   - Condense observations into a **single representative price** (copper).
   - Update `items.current_price` + `items.current_price_recorded_at`.
   - Insert a row into `item_price_history` (optional but recommended).
4. Public frontend reads via GET endpoints.

### 3.2 Components (logical)

**Auction client**
- Responsible only for calling Blizzard and returning parsed observations.

**Price aggregator**
- Converts a list of observations for one item into a single representative price.

**Price ingestion service**
- Orchestrates: fetch → group → aggregate → persist.

**Scheduler job**
- Runs ingestion service periodically.

**Admin API**
- CRUD for items/recipes, protected by JWT.

**Public API**
- GET-only endpoints for frontend.

---

## 4. Representative Price Algorithm (Commodities)

### 4.1 Requirements
- Output: **one universal** market value per item per snapshot
- Robust to a single poisoned low-price row
- Reflects reality that purchases consume the cheapest listings first
- Handles rows like: 17 @ 10,000 copper vs 156 @ 10,000,000 copper

### 4.2 Primary algorithm: Quantity-weighted median

Interpret each row as `quantity` units available at `unitPrice`. Let total quantity be:

`Q = Σ quantity_i`

Sort observations by `unitPrice` ascending. Compute cumulative quantity `C_k`. The **weighted median price** is the smallest `unitPrice_k` such that:

`C_k ≥ 0.5 * Q`

Properties:
- Strongly robust to outliers: a single tiny cheap row can’t move the median unless it contributes large volume.
- Still respects “cheap sells first”: price distribution is built from the cheapest upward.

Implementation notes:
- Ignore invalid rows where `unitPrice <= 0` or `quantity <= 0`.
- Use `long` for prices (copper) and `long` for cumulative quantity to avoid overflow.
- If Q is 0 after filtering, skip the item.

### 4.3 Fallback algorithm (optional): Trimmed VWAP of the cheapest volume

If you need a “more buy-now” oriented signal, compute a volume-weighted mean but only for the first X% of volume from the cheapest side (e.g., X=20%).

Steps:
1. Sort by price ascending.
2. Take rows until cumulative quantity reaches `target = X% * Q`.
3. Compute `VWAP = Σ(price * qty) / Σ(qty)` over those rows.

This tracks what a buyer is likely to pay for a moderate purchase, but is less robust than weighted median.

### 4.4 Stored value
- Store the chosen representative price as:
  - `items.current_price`
  - `items.current_price_recorded_at`
  - and optionally append to `item_price_history` with a `source` such as:
    - `BLIZZARD_COMMODITIES_WEIGHTED_MEDIAN`

---

## 5. API Design

### 5.1 Public (unauthenticated)
- `GET /items` (already exists)
- Recommended additions:
  - `GET /items/{id}`
  - `GET /recipes`
  - `GET /recipes/{id}`
  - `GET /items/{id}/price-history?limit=…`

### 5.2 Admin (JWT-authenticated, role-based)

All admin endpoints should live under `/admin/**`.

- Items CRUD
  - `POST /admin/items`
  - `PUT /admin/items/{id}`
  - `DELETE /admin/items/{id}`

- Recipes CRUD
  - `POST /admin/recipes`
  - `PUT /admin/recipes/{id}`
  - `DELETE /admin/recipes/{id}`

- Manual sync trigger (optional; scheduler remains core)
  - `POST /admin/ah/sync` (runs one ingestion cycle)

### 5.3 Later phase: bulk upload (same schema as Blizzard)

- `POST /admin/ah/commodities/bulk`
  - Body: Blizzard commodities schema (`{ auctions: [...] }`)
  - Processing: same ingestion service, but source set to `ADDON_BULK`.

---

## 6. Security Design (External JWT Issuer)

### 6.1 Requirements
- Frontend hosted separately from backend.
- Public endpoints are GET-only.
- Admin endpoints require JWT bearer tokens.

### 6.2 Recommended approach
Use Spring Security as an OAuth2 **Resource Server** validating JWTs from an external issuer.

- Protect `/admin/**`.
- Permit public GETs (e.g., `/items/**`, `/recipes/**`, `/health`).
- Stateless API:
  - No sessions.
  - CSRF disabled (since we use Authorization header bearer tokens).

### 6.3 CORS
Define a strict CORS policy:
- Allow only the frontend origin(s).
- Allow methods: `GET, POST, PUT, DELETE, OPTIONS`.
- Allow headers: `Authorization, Content-Type`.

---

## 7. Scheduler Design (Core)

### 7.1 Replace startup call
`AHDataFetcher` currently runs in `@PostConstruct` for quick testing. In production:
- Remove startup calls.
- Trigger via scheduler.

### 7.2 Scheduling options
- Fixed delay (simplest): every N minutes.
- Cron: e.g. every 10 minutes.

### 7.3 Operational safeguards
- Feature flag: `blizzard.sync.enabled=true|false`.
- Retry/backoff for transient failures.
- Timeouts on HTTP calls.
- Log structured summary per run: items updated, auctions processed, unknown item IDs.

---

## 8. Persistence Rules

### 8.1 Which items are updated
- Only items that exist in `items` are updated.
- Auction rows for unknown items are discarded.

### 8.2 Writes
Per item on each successful snapshot:
- Update:
  - `Item.currentPrice`
  - `Item.currentPriceRecordedAt` (prefer API timestamp if provided; otherwise `now()`)
- Insert one representative row into `item_price_history`:
  - `item_id`, `price`, `recorded_at`, `source`

### 8.3 No raw storage
Do not store any individual auction listings.

---

## 9. Implementation Steps (Milestones)

### Milestone A — Make ingestion production-shaped
1. Create a service that fetches item IDs from DB (replace placeholder in `AHDataFetcher`).
2. Introduce a scheduler job that calls ingestion periodically.
3. Persist representative prices to `items` and `item_price_history`.
4. Remove `@PostConstruct` startup call.

### Milestone B — Public data APIs
1. Add recipe GET endpoints.
2. Add item detail + price history endpoints.

### Milestone C — Admin auth + CRUD
1. Implement JWT resource server configuration.
2. Lock down `/admin/**`.
3. Add admin endpoints for item/recipe CRUD.

### Milestone D — Bulk upload readiness (later)
1. Add `POST /admin/ah/commodities/bulk` accepting Blizzard schema.
2. Reuse the same ingestion service, with a source identifier.

---

## 10. Testing Strategy

Leverage existing patterns in `src/test/java/com/crafting/**`.

- **Algorithm tests:** weighted median correctness on edge cases.
- **Ingestion tests (JPA):** one ingest cycle updates `items` and inserts history.
- **Security tests (WebMvc):**
  - `/items` GET is public
  - `/admin/**` returns 401 without JWT
  - `/admin/**` returns 403 with non-admin JWT
  - `/admin/**` returns 200 with admin JWT

---

## 11. Configuration & Deployment (Render)

- Use environment variables for secrets:
  - Blizzard: `BLIZZARD_CLIENT_ID`, `BLIZZARD_CLIENT_SECRET`
  - JWT issuer config (issuer URL / JWKS)
- Use a managed Postgres instance.
- Avoid embedding secrets in repo resources (do not rely on `src/main/resources/.env`).

---

## 12. Open Items (Track as decisions)

- What exact sync interval do we want (5 min, 10 min, 30 min)?
- Which timestamp to store as `recordedAt` (API timestamp vs server time)?
- Do we need a minimum volume threshold before we consider the price valid?
