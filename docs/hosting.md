# Hosting Guide (Laptop + Pi-hole + Postgres + Dockerized Backend)

This document is a step-by-step checklist for moving this backend to a 24/7 laptop that also runs Pi-hole.

**Target architecture recap**
- Backend: **Docker container** running this Spring Boot app
- Database: **Postgres running on the laptop** (host OS, not in Docker)
- Public clients:
  - Public frontend (hosted on Render or similar) does **GET** requests only
  - Admin frontend (also hosted separately) performs **CUD** operations via `/admin/**`
- Security:
  - `/admin/**` must be **JWT protected** using an **external issuer**
  - Public endpoints remain unauthenticated

---

## 0. Before you start (decisions)

1) **How will the laptop be reachable from Render?**
- Recommended: **Cloudflare Tunnel** (no port forwarding, stable URL, TLS)
- Alternative: DDNS + router port forwarding + reverse proxy (nginx/Caddy) + Let’s Encrypt

2) **Domains**
- Decide two hostnames:
  - `api.<your-domain>` → backend API
  - `admin.<your-domain>` → admin UI (if hosted separately)

3) **Frontend origins**
- Record exact frontend origins for CORS, e.g.
  - `https://your-frontend.onrender.com`
  - `https://admin-frontend.onrender.com`

---

## 1. Prepare the laptop OS

1) Update packages and reboot into a stable kernel.
2) Disable sleep/hibernation (the laptop must not suspend).
3) Ensure auto-start on power restoration (BIOS/UEFI setting if available).
4) Set up a firewall (UFW recommended).

**Pi-hole note:** Pi-hole uses port **53** (DNS) and often **80** for its admin UI. Avoid binding your backend or reverse proxy to ports that Pi-hole needs.

---

## 2. Install runtime dependencies

1) Install Docker Engine
- Follow the official Docker installation instructions for your distro.
- Confirm: `docker version` works.

2) Install Postgres on the host
- Prefer distro packages (systemd-managed Postgres).
- Confirm: `psql --version` and that `postgres` is running.

---

## 3. Configure Postgres (host database)

1) Create a database and a dedicated user
- Create DB: `crafting`
- Create user: `crafting_app`
- Grant only needed privileges on the `crafting` database.

2) Bind Postgres safely
- Keep Postgres listening on **localhost only** (`127.0.0.1`).
- Do **not** expose Postgres to the internet.

3) Plan growth
- `item_price_history` will grow over time. Decide retention policy early (e.g., keep 90 days or keep forever + add pruning later).

---

## 4. Build and run the backend container

### 4.1 Add a Dockerfile (project step)
If you don’t already have one, add a minimal Dockerfile to build and run the Spring Boot jar.

### 4.2 Run the container on the laptop
Recommended: run the container with environment variables and map it to a **high port** (example `8080`).

**Important:** If Pi-hole occupies port 80, do NOT map backend to 80. Keep it internal and publish only via tunnel/proxy.

Suggested environment variables for the container (examples):
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/crafting` (or use the host gateway IP)
- `SPRING_DATASOURCE_USERNAME=crafting_app`
- `SPRING_DATASOURCE_PASSWORD=<strong password>`
- `BLIZZARD_CLIENT_ID=<id>`
- `BLIZZARD_CLIENT_SECRET=<secret>`
- JWT resource server config (external issuer), typically:
  - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=<issuer>`
  - or `...JWK_SET_URI=<jwks>`

**Host → container DB connectivity (Linux note):**
- `host.docker.internal` may not exist on Linux by default.
- Common solution: run docker with `--add-host=host.docker.internal:host-gateway` and use that name in JDBC URL.

---

## 5. Configure Spring Boot for production

1) Use `application-prod.properties` for defaults, but keep secrets in env vars.
2) Ensure Flyway runs in prod.
3) Ensure scheduler can run (once implemented fully) and that it is feature-flagged.
4) Configure CORS for the Render frontend origin(s).

**Required before internet exposure:**
- Tighten security so `/admin/**` requires JWT.
- Ensure public endpoints (`/items`, `/recipes`, `/health`) stay reachable without auth.

---

## 6. Expose the API publicly (for Render)

### Option A (recommended): Cloudflare Tunnel
1) Create Cloudflare account and add your domain.
2) Install `cloudflared` on the laptop.
3) Create a tunnel and route a hostname (e.g., `api.<domain>`) to `http://localhost:8080`.
4) Enable HTTPS (Cloudflare handles TLS).
5) Optionally restrict admin endpoints by additional Cloudflare Access rules.

Benefits:
- No router config, no DDNS, stable URL, TLS handled.

### Option B: Port forwarding + reverse proxy
1) Set up DDNS or a static IP.
2) Configure router port forwarding to the laptop.
3) Run nginx/Caddy to terminate TLS and proxy to the backend.
4) Ensure ports don’t conflict with Pi-hole (Pi-hole often uses 80).

---

## 7. Admin operations (CUD endpoints)

1) Ensure admin frontend uses JWT and sends `Authorization: Bearer <token>`.
2) Backend must:
- validate JWTs from your external issuer
- authorize only admins for `/admin/**`
3) Confirm CORS allows `Authorization` header for admin origin.

---

## 8. Backups and recovery (must-have)

1) Automate daily backups
- Use `pg_dump` to produce a backup file.
- Store backups **off the laptop** (external drive + cloud).

2) Test restore regularly
- Restore into a new database occasionally to ensure backups are valid.

3) Monitor disk usage
- DB growth + logs can fill disk and break both the backend and Pi-hole.

---

## 9. Operational hardening

1) Run the backend container under a systemd service (or Docker restart policies)
- Use `restart: always` or systemd unit to keep it running.

2) Resource limits
- Apply memory/CPU limits to the backend container so it can’t starve Pi-hole DNS.

3) Logging
- Ensure logs are retained and rotated.

4) Health checks
- Monitor `GET /health` from an external uptime checker.

---

## 10. Post-move verification checklist

- Backend starts on boot and stays running
- Postgres is local-only and reachable from the container
- Migrations apply cleanly on an empty DB
- Public endpoints work from the internet:
  - `GET /health`
  - `GET /items`
  - `GET /recipes`
- Admin endpoints are NOT publicly writable without JWT:
  - `POST /admin/items` should be rejected without token
- CORS works from Render frontend origin(s)
- Backups run automatically and restore succeeds
- Pi-hole DNS continues to function under backend load
