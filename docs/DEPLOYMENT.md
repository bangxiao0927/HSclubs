# HSclubs — Single-School Deployment Guide

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────┐
│   Browser    │────▶│ Reverse proxy│────▶│  Vue SPA │
│              │     │   :80/:443   │     │  :4173   │
└──────────────┘     └──────┬───────┘     └──────────┘
                            │
                            ▼
                     ┌──────────────┐     ┌──────────┐
                     │ Spring Boot  │────▶│  MySQL   │
                     │   :8080      │     │  :3306   │
                     └──────────────┘     └──────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │ Google OAuth │
                     └──────────────┘
```

## Prerequisites

| Component            | Minimum Version             | Purpose                    |
| -------------------- | --------------------------- | -------------------------- |
| Java                 | 17+                         | Backend runtime            |
| MySQL                | 8.0+                        | Database                   |
| Node.js              | See `frontend/package.json`'s `engines.node` (currently `^20.19.0 \|\| >=22.12.0`) | Frontend build |
| npm                  | 10+                         | Frontend dependencies      |
| Python               | 3.10+ with `venv` and `pip` | Instaloader avatar cache   |
| Caddy or nginx       | Caddy 2.7+ / nginx 1.24+    | Reverse proxy (production) |
| Google Cloud Project | —                           | OAuth2 credentials         |
| Domain               | —                           | HTTPS + OAuth redirect     |

`deploy-main.sh` checks the active Node.js against that `engines.node` range
before building the frontend. If the server's default Node.js does not
satisfy it (for example an older LTS installed via the OS package manager),
the script activates a compatible version through
[nvm](https://github.com/nvm-sh/nvm) instead of failing outright -- see
"Node.js runtime selection" below.

---

## This deployment: MVHS on `mvhs.hsclubs.net`

The repository is generic, but the instance it was written for is one school. As of 2026-08 that
school is **Mountain View High School** and it answers on **`mvhs.hsclubs.net`**. The apex
`hsclubs.net` is *not* this app -- it is the guiding page (the cross-school directory), which is
also where the iOS app's Universal Link callback lives. The two used to be the other way round
(`hsclubs.net` was this site, the directory was on `clubs.bangxiao.net`), so anything older than
that date, and any bookmark, will still point at the previous arrangement.

The identity below ships as the built-in default, so a deployment that sets none of it is already
correct. Set them explicitly anyway if you would rather not depend on a default:

```bash
# backend/.env
APP_SUMMARY_SCHOOL_NAME=Mountain View High School
APP_SUMMARY_SHORT_NAME=MVHS
APP_SUMMARY_SLUG=mvhs
FRONTEND_ORIGIN=https://mvhs.hsclubs.net
APP_SCHOOL_SITE_ORIGIN=https://mvhs.hsclubs.net
APP_MOBILE_AUTH_CALLBACK_URLS=https://hsclubs.net/mobile-auth/callback
```

```bash
# frontend/.env.production
VITE_SCHOOL_NAME=Mountain View High School
VITE_SCHOOL_SHORT_NAME=MVHS
```

`APP_SUMMARY_SLUG` is not cosmetic. The guiding page rejects a summary whose slug disagrees with
the registry entry it was fetched for, so the slug here and the slug in the registry move
together or the school drops out of the directory.

### Moving the site to a new hostname

Changing the public host is not only DNS, because two other systems have the old one recorded:

1. **Certificate and proxy.** Issue the certificate for the new name and add it to the Caddy site
   block or nginx `server_name`. Keep the old name served and 301-redirecting for as long as
   links to it exist.
2. **OAuth.** Add `https://<new-host>/api/auth/google/callback` to the Google Cloud OAuth client's
   authorized redirect URIs **before** cutting over; sign-in breaks the moment the origin changes
   otherwise. Remove the old entry only after the redirect is retired.
3. **`FRONTEND_ORIGIN` / `APP_SCHOOL_SITE_ORIGIN`.** The first is the CORS and post-login origin;
   the second is what the manifest publishes. Neither is derived from the request's `Host`.
4. **The origin challenge.** Verification proves control of a *host*, so the new hostname counts
   as unverified until it is re-checked. The token itself travels with the build
   (`frontend/public/.well-known/hsclubs-site.txt`), so nothing has to be republished by hand --
   but the registry's `summaryUrl` must be pointed at the new host and the guiding page's
   `npm run verify` re-run, or the school drops out of the directory.
5. **Rebuild the frontend.** `VITE_API_BASE_URL` and the branding are baked in at build time.

---

## Environment Variables

### Backend (`backend/.env`)

Spring Boot loads this file automatically via `spring.config.import: optional:file:.env[.properties]`.

```bash
# === Required ===

# Google OAuth2 credentials from Google Cloud Console
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxx

# MySQL connection
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/mydb?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME=root
DB_PASSWORD=your-secure-password

# === Platform Owners ===
# Comma-separated email addresses of platform owners
APP_OWNER_EMAILS=admin@example.com,owner@school.edu

# === Optional ===

# Frontend origin for CORS and OAuth redirect
FRONTEND_ORIGIN=http://localhost:4173

# OAuth post-login redirect target
# Defaults to ${FRONTEND_ORIGIN}/auth/callback
# APP_POST_LOGIN_REDIRECT_URI=http://localhost:4173/auth/callback

# Custom OAuth authorization base path
# APP_AUTHORIZATION_REQUEST_BASE_URI=/api/auth/authorize
```

### Restricting who may sign in

By default **any** account the OAuth provider authenticates may sign in, which is what a fresh
copy of this repo should do. A school running its own site usually wants that limited to its
own Google Workspace:

```bash
# Comma-separated; empty (the default) means no restriction
APP_ALLOWED_EMAIL_DOMAINS=students.example.edu,example.edu

# Optional: also require the provider to report the address as verified
APP_REQUIRE_VERIFIED_EMAIL=true
```

Only the domain after the last `@` is compared, case-insensitively, so
`ada@students.example.edu.evil.com` does not pass a `students.example.edu` restriction. A
rejected sign-in never creates an `oauth_users` row, and the student is returned to the sign-in
page with an explanation rather than a generic "try again".

Note this is also the boundary `APP_OWNER_EMAILS` sits behind: platform-owner status is decided
by comparing the signed-in email address, so restricting the domain restricts who can even
attempt to hold that address.

The restriction is applied when an account signs in, not on every request, so it does not by
itself evict someone who is already signed in — sessions last 7 days. In practice enabling it
means restarting the backend, and sessions are held in the application's own memory (there is no
external session store), so every existing session is dropped by that restart anyway. If you
ever change this setting without a restart, expect existing sessions from a now-disallowed
domain to survive until they expire.

### Optional internal App Review account

A deployment can expose one fixed email/password account for App Review. This is not public
registration and is disabled unless both the email and a BCrypt password hash are configured.
Never put the plaintext password in the repository or in `backend/.env`.

Generate a long random password, save it in the private App Store Connect review notes, and hash
it. Any BCrypt implementation will do; pick whichever tool the machine already has. Generate the
password with `openssl rand -base64 24` (or a password manager) rather than inventing one:

```bash
# Python, which every deployment already has (the Instaloader setup installs it):
python3 -c 'import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(12)).decode())' \
  'choose-a-long-random-password'

# ...or htpasswd, if apache2-utils happens to be installed:
htpasswd -bnBC 12 '' 'choose-a-long-random-password' | tr -d ':\n'
```

`apache2-utils` (which provides `htpasswd`) is not installed on a stock server, so the Python
form is the one to reach for first. Hash on the machine you generated the password on, and pass
the password as a shell argument only if you are comfortable with it reaching that shell's
history; otherwise let the tool prompt for it.

Then add the result to the server's private `backend/.env`:

```bash
APP_INTERNAL_REVIEW_EMAIL=app-review@example.edu
APP_INTERNAL_REVIEW_DISPLAY_NAME=App Review
APP_INTERNAL_REVIEW_PASSWORD_HASH=$2y$12$...
```

Restart the backend. `GET /api/auth/providers` then includes `internal`, and `/auth` shows a
"Sign in with password" option beneath Google. It opens `/auth/password`, where the reviewer enters
the supplied credentials. The endpoint accepts JSON only, applies BCrypt verification, returns a
generic error for either a wrong email or password, rotates the session before authentication, and
creates the same normal application session used by Google login.

Repeated failures are throttled twice over: 5 within 15 minutes blocks that client address, and 20
within 15 minutes blocks the account for everyone. The second cap exists because the client address
is only as trustworthy as the reverse proxy in front of it (see the `X-Forwarded-For` requirement
under "Reverse Proxy"): where that requirement is not met, a caller can pick their own address and
would otherwise sidestep the per-client count entirely. Both counts clear on a successful sign-in
and both expire on their own, so neither can lock a reviewer out permanently.

If you enable `APP_CSRF_ENABLED`, note that this endpoint is protected like every other write, so
the frontend must send the `X-XSRF-TOKEN` header for password sign-in to work -- the same
outstanding frontend change that flag already waits on.

Important: a review account helps Apple access protected features, but it does **not** by itself
satisfy App Review guideline 4.8 when Google is offered as a third-party/social login. Whether
the app qualifies for one of Apple's exceptions is a separate App Review decision.

Suggested App Review notes (put the actual email and plaintext password in App Store Connect,
never in this repository):

```text
The app can be browsed without signing in. Sign-in is required for Profile, club applications,
and publishing or managing club content. On the sign-in page, choose "Sign in with password",
then use the review email and password supplied below. The account has no two-factor
authentication or additional verification step.
```

### Production session cookie

Set this on any HTTPS deployment:

```bash
SESSION_COOKIE_SECURE=true
```

It defaults to `false` so that plain-HTTP local development still gets a session at all (a
`Secure` cookie is dropped by the browser on `http://localhost`). The session cookie lives for
7 days, so leaving it unset in production means that cookie is eligible to be sent over a
plain-HTTP request.

This depends on the reverse proxy forwarding the original scheme: the backend sits behind TLS
termination and would otherwise see every request as insecure. The app reads
`X-Forwarded-Proto` (`server.forward-headers-strategy: framework`). Caddy's `reverse_proxy`
sets that header itself, so nothing extra is needed there; nginx has to be told to send it on
every proxied location (see both examples below).

The H2 console is not part of a production deployment. It is enabled only by the `h2`
profile; do not set `SPRING_PROFILES_ACTIVE=h2` on a server, since that profile also points
the datasource at an in-memory database and runs the destructive local schema fixture.

### Instaloader avatar cache

The Linux initializer creates a private Python virtual environment, installs
Instaloader and browser-cookie support, and can update the backend environment
file. Install the operating-system packages first:

```bash
# Ubuntu, Debian, Linux Mint, or Pop!_OS
sudo apt update
sudo apt install -y python3 python3-venv python3-pip

# Fedora, RHEL, Rocky Linux, AlmaLinux, or CentOS Stream
sudo dnf install -y python3 python3-pip

# Arch Linux or Manjaro
sudo pacman -S --needed python python-pip

# openSUSE or SLES
sudo zypper install python3 python3-pip python3-virtualenv
```

Initialize the environment and write the absolute Python command to
`backend/.env`:

```bash
cd /opt/hsclubs
./scripts/setup-instaloader.sh --configure-env
./scripts/setup-instaloader.sh --configure-env --check
```

The script is idempotent. It skips package installation when the virtual
environment already matches `backend/requirements-instaloader.txt`. Use
`PYTHON_BIN`, `INSTALOADER_VENV_DIR`, or `BACKEND_ENV_FILE` to override its
defaults.

Environment values read by the deployment scripts may be unquoted or enclosed
in one matching pair of single or double quotes. Complex embedded quoting and
backslash escapes are rejected instead of being interpreted differently from
systemd.

Instaloader requires authentication. For a headless systemd host, create the
session as the same account that runs the backend so the service can read it:

```bash
cd /opt/hsclubs
install -d -m 0700 "$HOME/.config/instaloader"
./backend/.venv/bin/instaloader \
  --sessionfile "$HOME/.config/instaloader/session-your_instagram_username" \
  --login your_instagram_username
chmod 0600 "$HOME/.config/instaloader/session-your_instagram_username"
```

Add the session identity and explicit file path to `backend/.env`:

```bash
APP_INSTAGRAM_AVATAR_SESSION_USER=your_instagram_username
APP_INSTAGRAM_AVATAR_SESSION_FILE=/home/your-deploy-user/.config/instaloader/session-your_instagram_username
```

These commands assume the backend uses the default deployment account. Run them
as the same non-root account that invokes `deploy-main.sh`. If
`BACKEND_RUN_USER` explicitly selects another service account, create the
session as that account instead and use its home directory in
`APP_INSTAGRAM_AVATAR_SESSION_FILE`.

When a session user is configured, `deploy-main.sh` asks Instaloader to load the
session as the final backend service account before building or restarting the
service. Deployment stops if that account cannot traverse the session path,
read the file, or parse its contents. If no explicit session file is configured,
the check uses Instaloader's default session location for the service account.

For local Linux development, browser cookies are also supported with
`APP_INSTAGRAM_AVATAR_COOKIE_BROWSER=firefox` (or `chrome`, `edge`,
`brave`, and the other supported browser values). A saved session is preferred
on servers because browser profiles usually do not exist there.

Do not commit session files or browser cookies. The repository ignores
`backend/.venv/` and `backend/.instaloader/`. If authentication is missing or
expired, the backend uses its web-profile fallback or a generated placeholder.

### Frontend (`frontend/.env` or `.env.production`)

```bash
# Backend API base URL (defaults to http://localhost:8080)
VITE_API_BASE_URL=http://localhost:8080

# For production builds, set to your backend URL:
# VITE_API_BASE_URL=https://api.yourdomain.com

# Calendar schedule in 24-hour HH:mm format:
VITE_CALENDAR_LUNCH_START=11:30
VITE_CALENDAR_LUNCH_END=13:30
VITE_CALENDAR_AFTER_SCHOOL_START=15:10
VITE_CALENDAR_AFTER_SCHOOL_END=18:00
```

Vite embeds these values during `npm run build`. Rebuild and republish the frontend after changing them.

---

## Database Setup

### Production and new-school MySQL setup

The backend no longer auto-creates the MySQL schema. `backend/src/main/resources/schema.sql`
and `data.sql` are an **H2-only local dev fixture** (see the file header in `schema.sql` for
why) — `spring.sql.init.mode` defaults to `never` in production
(`backend/.env` also pins `SPRING_SQL_INIT_MODE=never` explicitly), and that script has never
actually run successfully against real MySQL: it uses `CLOB` for `clubs.achievements`, where
production uses `json NOT NULL DEFAULT (json_array())`.

Set up a new production (or new-school) database with explicit SQL instead:

```sql
CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

1. Create the tables with your own MySQL-correct `CREATE TABLE` statements (there is currently
   no committed, production-verified DDL script for this — see the note below).
2. Seed initial club data with `mvhs_clubs_seed.sql` at the repository root (generated by
   `scripts/generate_clubs_sql.py` from the official MVHS clubs CSV):
   ```bash
   mysql -u root -p mydb < mvhs_clubs_seed.sql
   ```
3. Start the backend against that database. With `SPRING_SQL_INIT_MODE=never` (the default), it
   will not attempt to create or alter any tables on startup.

> **TODO**: There is no committed, production-verified `CREATE TABLE` script for MySQL. The
> `docs/schema-migration.sql` ALTER script below was written for a different (multi-school)
> baseline and does not match the current single-school production schema either. Producing a
> real DDL dump from production (including confirming whether `schools` / `school_users` exist
> there) is tracked as follow-up work, not done as part of this change.

### Existing database (additive migration)

If you already have a running instance from an older version, `docs/schema-migration.sql` has
`ALTER TABLE` / `CREATE TABLE IF NOT EXISTS` statements for some later columns. Review it against
your actual schema before running it — it was written against a multi-school baseline that may
not match a single-school deployment.

```bash
mysql -u root -p mydb < docs/schema-migration.sql
```

### Verify

```sql
-- Check clubs
SELECT id, name, slug, category, status FROM clubs LIMIT 5;

-- Check users
SELECT uid, email, display_name, provider FROM oauth_users LIMIT 5;

-- Check user profiles (includes graduation year)
SELECT up.oauth_user_id, ou.email, up.graduation_year
FROM user_profiles up
JOIN oauth_users ou ON ou.uid = up.oauth_user_id
LIMIT 5;

-- Check club members
SELECT ou.email, cm.role_name, c.name
FROM club_member cm
JOIN oauth_users ou ON ou.uid = cm.oauth_user_id
JOIN clubs c ON c.id = cm.club_id
LIMIT 5;
```

---

## Backend Deployment

### Local development

```bash
cd backend
cp .env.example .env  # edit with your credentials
./mvnw spring-boot:run
# Starts on http://localhost:8080
```

### Production build

```bash
cd backend
./mvnw package -DskipTests
# Produces target/backend-0.0.1-SNAPSHOT.jar
```

### Run as a service (systemd)

Create `/etc/systemd/system/hsclubs.service`. The sample below is byte-for-byte
what `scripts/deploy-main.sh`'s `backend_service_unit_content` function
generates for the documented defaults: `APP_DIR=/opt/hsclubs`,
`BACKEND_RUN_USER=your-deploy-user`, `SYSTEMD_SCOPE=system`, and a `java`
resolved from `PATH` at `/usr/bin/java`. `restart_or_require_admin_setup`
compares this file byte-for-byte against the installed unit before every
restart, so any customization -- a different `APP_DIR`, `BACKEND_RUN_USER`,
`BACKEND_ENV_FILE`, or `java` location -- changes the exact unit the script
expects. Run `scripts/deploy-main.sh` once with your real environment
variables set and read the generated unit from the "Generated the desired
unit at" message it prints instead of hand-editing this sample, if your
deployment differs from the defaults above.

```ini
[Unit]
Description=HSclubs Backend
Wants=network-online.target
After=network-online.target mysql.service

[Service]
Type=simple
User=your-deploy-user
WorkingDirectory=/opt/hsclubs/backend
EnvironmentFile=/opt/hsclubs/backend/.env
ExecStart=/usr/bin/java -jar /opt/hsclubs/backend/target/demo-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

The `ExecStart` jar filename comes from your Maven build (`target/*.jar`) and
is resolved at deploy time, so it will differ from the example above only if
`backend/pom.xml`'s `artifactId` or `version` changes.

```bash
sudo systemctl daemon-reload
sudo systemctl enable hsclubs
sudo systemctl start hsclubs
sudo systemctl status hsclubs
```

The repository also includes `scripts/deploy-main.sh`, which initializes Instaloader when
the avatar cache is enabled, builds both applications, installs or updates the persistent
service, checks the backend, and then publishes the frontend. Its production defaults use
the system service and, for a fresh installation, the non-root account that invokes the
deploy script. If the service already exists, deployment preserves its configured `User=`
unless `BACKEND_RUN_USER` is explicitly set. Run the script directly from the deployment
account. For a system-scope service that is already installed, enabled, and unchanged, the
only privileged step it runs is the exact command granted by
`ops/hsclubs-deploy.sudoers` -- see "Restricted sudo for system-scope deploys" below. Set
`BACKEND_RUN_USER` only when the backend should use a different, existing account or when
intentionally migrating an existing service:

```bash
./scripts/deploy-main.sh

# Explicitly use a separate service account.
BACKEND_RUN_USER=your-service-user ./scripts/deploy-main.sh

# Explicitly migrate an existing service to the deployment account after
# granting that account access to uploads, sessions, and other runtime files.
BACKEND_RUN_USER="$(id -un)" ./scripts/deploy-main.sh

# Skip Python initialization only when Instaloader is managed separately.
SETUP_INSTALOADER=0 ./scripts/deploy-main.sh
```

Do not run the entire script with `sudo` or from a root login. It exits before
fetching or building in that case, preventing root-owned source, virtual
environment, and build artifacts. Run it directly from the intended non-root
deployment account and enter sudo credentials only when the script requests
them.

After building the backend and before replacing or restarting the unit, the
deploy script verifies that the final service account can enter the working
directory, read the JAR, execute Java and the configured Instaloader runtime,
and write to or create the configured upload directory. For a system service,
systemd reads the environment file before changing to the configured service
account, so the file only needs to be readable by the deployment account and
system manager.

The deploy script respects `APP_INSTAGRAM_AVATAR_CACHE_ENABLED=false` and skips
Instaloader initialization when the cache is disabled.

### Restricted sudo for system-scope deploys

A system-scope unit is root-owned. Installing, enabling, and reloading it is a one-time
admin task, done by hand once; routine deploys afterward must not repeat those privileged
steps. `scripts/deploy-main.sh` enforces that split:

- It generates the unit it would install into a temp file and compares it against
  `/etc/systemd/system/$BACKEND_SERVICE`.
- If the installed unit is identical and already enabled, it runs exactly one privileged
  command: `sudo /usr/bin/systemctl restart hsclubs.service` (the `systemctl` path is
  resolved and canonicalized at run time so it matches the sudoers rule below).
- If the unit is missing, different, or not enabled, it fails immediately with the exact
  `install`/`daemon-reload`/`enable`/`start` commands an admin needs to run once, instead of
  attempting a broad privileged install itself.
- When `BACKEND_RUN_USER` is the account running the deploy (for example `webowner` running
  as `webowner`), the runtime-access checks that back it call the backend directly instead
  of through `sudo -u`, since no privilege boundary needs crossing.

One-time admin setup, after creating the unit shown under "Run as a service" above:

```bash
sudo systemctl daemon-reload
sudo systemctl enable hsclubs.service
sudo systemctl start hsclubs.service

# Validate the sudoers rule before installing it.
sudo visudo -cf ops/hsclubs-deploy.sudoers
sudo install -m 0440 ops/hsclubs-deploy.sudoers /etc/sudoers.d/hsclubs-deploy
```

`ops/hsclubs-deploy.sudoers` grants exactly one command to the deployment account, with no
wildcard and no shell or helper script:

```
webowner ALL=(root) NOPASSWD: /usr/bin/systemctl restart hsclubs.service
```

After that one-time setup, every routine deploy needs only that single restart command as
root; it never re-installs, re-enables, or daemon-reloads the unit.

### Node.js runtime selection

Before installing frontend dependencies or building, `deploy-main.sh` checks
the Node.js on `PATH` against `frontend/package.json`'s `engines.node` range.

- If it already satisfies the range, the script uses it as-is.
- If it does not, the script sources `nvm.sh` from `$NVM_DIR/nvm.sh` (default
  `$HOME/.nvm/nvm.sh`) and activates the highest already-installed nvm
  version that satisfies the range.
- Set `DEPLOY_NODE_VERSION` to force a specific nvm-managed version instead
  (for example `DEPLOY_NODE_VERSION=22.12.0`). The script still verifies that
  version satisfies `engines.node` before using it.

The script never installs a Node.js version over the network: if no
already-installed version (system or nvm) satisfies the requirement, or nvm
itself is not installed at the expected path, it fails with a message naming
the required range and, when nvm is available, the versions it found
installed. Install a compatible version with `nvm install <version>` and
re-run the deploy, or set `DEPLOY_NODE_VERSION` to a version you have already
installed.

```bash
# Force a specific already-installed nvm version.
DEPLOY_NODE_VERSION=22.12.0 ./scripts/deploy-main.sh

# Use a non-default nvm installation location.
NVM_DIR=/opt/nvm ./scripts/deploy-main.sh
```

### Health check

```bash
# List clubs (public endpoint)
curl http://localhost:8080/api/clubs

# Check auth providers
curl http://localhost:8080/api/auth/providers
```

### Sizing guidelines

| School count | RAM    | CPU      |
| ------------ | ------ | -------- |
| 1–10         | 512 MB | 1 vCPU   |
| 10–50        | 1 GB   | 2 vCPU   |
| 50+          | 2 GB+  | 2–4 vCPU |

### Deployment history

Every run of `scripts/deploy-main.sh` appends one line to `deploy-history.log` in the
repository root (override the path with `DEPLOY_HISTORY_FILE`): a UTC timestamp, `success` or
`failure`, and the deployed commit's sha, tab-separated. An `EXIT` trap records the final result,
so failures that do not trigger the diagnostic `ERR` trap (including an explicit `exit`) are
still recorded. Recording itself is best-effort: a
missing `git` command or an unwritable history path never aborts a deploy.

```bash
tail deploy-history.log
# 2025-01-15T03:00:12Z    success    3f2c1a9b8e7d6c5b4a3928170665544332211000
# 2025-01-16T03:00:08Z    failure    3f2c1a9b8e7d6c5b4a3928170665544332211000
```

`deploy-history.log` matches the repository's `*.log` gitignore pattern, so it is never
committed; it is a local audit trail on each server, not shared history.

---

## Frontend Deployment

### Build

```bash
cd frontend
npm install
npm run build
# Output: dist/
```

Set `VITE_API_BASE_URL` before building for production:

```bash
VITE_API_BASE_URL=https://api.yourdomain.com npm run build
```

Or create `frontend/.env.production`:

```bash
VITE_API_BASE_URL=https://api.yourdomain.com
```

### Reverse proxy

Any reverse proxy works. The requirements the application actually has are:

- Serve `frontend/dist` as static files with an SPA fallback to `index.html`.
- Forward `/api/**`, `/oauth2/**`, and `/uploads/**` to the backend on `127.0.0.1:8080`.
  `/uploads/**` is easy to forget: club photos are served from there by the backend, not from
  the built frontend.
- Forward `/.well-known/hsclubs-app.json` to the backend. It is the v1 identity manifest the
  guiding page reads, and it cannot fall through to the SPA fallback (the registry expects JSON,
  not the index document). The origin-challenge file beside it,
  `/.well-known/hsclubs-site.txt`, stays a static file under `frontend/public/`, so do not proxy
  the whole `/.well-known/` directory.
- Send the original scheme through as `X-Forwarded-Proto`, so the backend knows the request
  arrived over HTTPS (see "Production session cookie" above).
- Set `X-Forwarded-For` to the address the proxy itself observed, **replacing** whatever the
  client sent. The backend runs with `server.forward-headers-strategy: framework`, so
  `HttpServletRequest#getRemoteAddr()` returns the *first* entry of that header. Anything that
  appends to a client-supplied value therefore hands the caller control of the address the
  application sees, which is what the password sign-in throttle keys on.
- Terminate TLS.

#### Caddy (what this deployment runs)

Caddy is the proxy the MVHS deployment runs and the one to copy for a new school. It is also the
smaller configuration, because it obtains and renews certificates itself and `reverse_proxy`
already sets `X-Forwarded-Proto`, `X-Forwarded-For`, and `X-Forwarded-Host` — and, with no
`trusted_proxies` configured, it replaces a client-supplied `X-Forwarded-For` rather than
appending to it, which is the behaviour the bullet above asks for.

Example `/etc/caddy/Caddyfile`:

```caddy
yourdomain.com {
    encode gzip

    # Backend: API, the OAuth2 handshake, and stored club photos
    handle /api/* {
        reverse_proxy 127.0.0.1:8080
    }
    handle /oauth2/* {
        reverse_proxy 127.0.0.1:8080
    }
    handle /uploads/* {
        reverse_proxy 127.0.0.1:8080
    }
    handle /.well-known/hsclubs-app.json {
        reverse_proxy 127.0.0.1:8080
    }

    # Frontend static files, with the SPA fallback
    handle {
        root * /var/www/hsclubs/frontend/dist
        try_files {path} /index.html
        file_server
    }
}
```

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

With this single-origin layout the frontend build needs no absolute API base URL: leave
`VITE_API_BASE_URL` empty (or set it to `https://yourdomain.com`) and set
`FRONTEND_ORIGIN=https://yourdomain.com`.

#### Nginx (alternative)

Only if the host already runs nginx for something else. Note the `X-Forwarded-For` line below:
it is `$remote_addr`, **not** the usual `$proxy_add_x_forwarded_for`. The usual one appends the
peer address to whatever the client sent, so the client's own value stays first — and the first
entry is exactly what the backend reads as the remote address. With `$proxy_add_x_forwarded_for`
here, anyone can pick the address this application sees by sending the header themselves, which
defeats the per-address throttle on `POST /api/auth/internal/login`.

Example `/etc/nginx/sites-available/hsclubs`:

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    # Frontend static files
    root /var/www/hsclubs/frontend/dist;
    index index.html;

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy to backend
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 callback passthrough
    location /oauth2/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        # Required, like the /api/ block above: without the original scheme the backend
        # treats the request as plain HTTP, so JSESSIONID loses its Secure attribute and
        # any absolute URL the app builds during the OAuth handshake comes out as http://.
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Stored club photos, served by the backend
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # The v1 identity manifest is backend JSON, not a static asset.
    location = /.well-known/hsclubs-app.json {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Gzip
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 256;
}
```

```bash
sudo ln -s /etc/nginx/sites-available/hsclubs /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

#### TLS

Caddy provisions and renews certificates on its own; nothing else to do.

For nginx, use Certbot:

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

---

## OAuth2 Configuration

### Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create a project or select existing
3. Configure OAuth consent screen:
   - Type: **External**
   - Scopes: `email`, `profile`, `openid`
   - Add test users during development
4. Create OAuth 2.0 Client ID:
   - Application type: **Web application**
   - Authorized redirect URIs:
     ```
     http://localhost:8080/api/auth/google/callback   (dev)
     https://yourdomain.com/api/auth/google/callback   (prod)
     ```
5. Copy **Client ID** and **Client Secret** into `backend/.env`

### Redirect flow

```
User clicks "Sign in with Google"
  → GET /oauth2/authorization/google
  → Google OAuth consent screen
  → Google redirects to /api/auth/google/callback
  → Spring Security exchanges code for token
  → Redirect to FRONTEND_ORIGIN/auth/callback
  → Frontend reads /api/auth/me
```

### Adding more OAuth providers

Add provider config in `application.yaml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user,user:email
```

The `/api/auth/providers` endpoint auto-discovers all configured providers.

---

## Production Observability

Two standalone scripts, installable as either systemd `--user` timers or cron entries with no
root privileges, cover the two things a production deployment needs watched: is it up, and is
the database backed up.

### Health monitoring (`scripts/health-check.sh`)

Checks the production HTTP frontend and backend API, plus the `hsclubs`, `caddy`, and `mysql`
systemd services, and records the result of every check:

```bash
./scripts/health-check.sh
```

| Variable                      | Default                                              | Purpose |
| ------------------------------ | ----------------------------------------------------- | ------- |
| `FRONTEND_HEALTH_URL`          | `http://127.0.0.1/`                                   | Local Caddy frontend endpoint to probe (its HTTPS redirect counts as healthy) |
| `BACKEND_HEALTH_URL`           | `http://127.0.0.1:8080/api/clubs`                     | Backend API endpoint to probe |
| `HEALTH_CHECK_SERVICES`        | `hsclubs.service caddy.service mysql.service`         | Space-separated systemd units to check |
| `HEALTH_CHECK_SYSTEMD_SCOPE`   | `system`                                              | `system` or `user`, matching how the backend service is installed |
| `HEALTH_CHECK_STATE_DIR`       | `$XDG_STATE_HOME/hsclubs/health-check` (or `~/.local/state/...`) | Where each check's last status is recorded |
| `HEALTH_CHECK_WEBHOOK_URL`     | unset                                                 | Generic webhook endpoint for alerts |

A webhook alert (a JSON POST) is sent only when a check's status **changes** — `ok` to `fail`,
or `fail` back to `ok` — never on every run, so a steady outage does not spam the webhook once
per minute. Reading the current status still works with no webhook configured at all; the
script just logs the transition instead of delivering it anywhere. **No webhook URL is
hardcoded or committed anywhere in this repository** — set `HEALTH_CHECK_WEBHOOK_URL` in the
environment, or in the private, ungitignored-by-necessity file described under "Installing
periodic checks" below.

The script exits non-zero if any check currently fails (useful for cron's own failure
notifications), independent of whether an alert was sent for that run.

`curl` is a hard requirement: without it every HTTP probe would fail exactly like a real
outage, so the script exits with `Missing required command: curl` rather than reporting a
false negative.

### MySQL backups (`scripts/backup-mysql.sh`)

Reads `SPRING_DATASOURCE_URL`, `DB_USERNAME`, and `DB_PASSWORD` from `backend/.env` **without
sourcing it** (the same technique `deploy-main.sh` uses for its own environment values), and
writes a gzip-compressed `mysqldump` of the production database:

```bash
./scripts/backup-mysql.sh
```

| Variable                | Default                                         | Purpose |
| ------------------------ | ------------------------------------------------ | ------- |
| `BACKEND_ENV_FILE`       | `backend/.env`                                   | Where to read the datasource URL and credentials from |
| `BACKUP_DIR`             | `$XDG_STATE_HOME/hsclubs/backups` (or `~/.local/state/...`) | Where backups are written |
| `BACKUP_RETENTION_DAYS`  | `14`                                             | Backups older than this are deleted after a successful run |
| `LOCK_FILE`              | `$BACKUP_DIR/.backup.lock`                       | Overlap lock (see below) |

Safety properties:

- The password is passed to `mysqldump` through a private, mode-0600 `--defaults-extra-file`,
  never as a `--password=` command-line argument (which would be visible to other users via
  `ps`) and never as a plain environment variable.
- The dump is written to a temp file in `BACKUP_DIR`, gzip'd, and validated with `gzip -t`
  before being renamed into place; a corrupt or partial backup is never left at the final path,
  and the final `mv` is an atomic rename because the temp file and the destination share a
  filesystem.
- The final backup file and `BACKUP_DIR` itself are mode 0600/0700.
- A `flock`-based lock makes an overlapping run (for example a slow backup still running when
  the next scheduled one starts) exit immediately instead of racing the first one.

### Installing periodic checks and backups (`scripts/install-observability.sh`)

Installs both scripts to run periodically for the current user, with no root privileges
required at all:

```bash
# Auto-detects persistent systemd --user (lingering enabled), otherwise uses cron.
./scripts/install-observability.sh

# Force a mode explicitly.
./scripts/install-observability.sh --mode=systemd-user
./scripts/install-observability.sh --mode=cron

# Change the schedule.
./scripts/install-observability.sh --health-interval=2min --backup-schedule='*-*-* 03:30:00'

# For a schedule with no unambiguous cron equivalent (see below), set the
# cron schedule directly instead.
./scripts/install-observability.sh --mode=cron --health-cron-schedule='*/2 * * * *' \
  --backup-cron-schedule='30 3 * * *'

# Remove everything this script installed.
./scripts/install-observability.sh --uninstall
```

Under systemd `--user`, it writes `hsclubs-health-check.timer`/`.service` and
`hsclubs-backup.timer`/`.service` to `~/.config/systemd/user` and enables them with
`systemctl --user`. Under cron, it adds two lines to the current user's crontab, each tagged
with a marker comment so re-running install (or `--uninstall`) only ever touches its own lines
and never disturbs anything else already in that crontab. Cron jobs use a small `/bin/sh`
wrapper to load the same optional environment file as the systemd services before executing
the health check or backup script.

Under cron mode, `--health-interval` and `--backup-schedule` are translated to an equivalent
cron schedule automatically (an exact `Nmin`/`Nh` duration where `N` evenly divides 60/24
respectively, and a daily `*-*-* HH:MM:SS`
`OnCalendar` expression, respectively). A schedule with no unambiguous cron equivalent —
seconds, days, a non-divisor `N` such as `7min` or `7h`, a weekday filter, multiple times a
day, and so on — makes the install fail with
an actionable error instead of silently installing the default schedule; pass
`--health-cron-schedule` or `--backup-cron-schedule` (standard 5-field cron syntax) directly in
that case.

Both installation modes load an optional, private environment file —
`~/.config/hsclubs/observability.env` by default — for secrets such as
`HEALTH_CHECK_WEBHOOK_URL`. Create it yourself, outside the repository, with restrictive
permissions:

```bash
mkdir -p ~/.config/hsclubs
cat > ~/.config/hsclubs/observability.env <<'ENV'
HEALTH_CHECK_WEBHOOK_URL=https://your-alerting-endpoint.example/webhook
ENV
chmod 600 ~/.config/hsclubs/observability.env
```

Its absence is not an error: both scripts run fine with just their built-in defaults.

In cron mode the application directory and the env file path are written into the crontab
command field, where cron treats an unescaped `%` as a newline. The installer escapes `%`
automatically, but it refuses to install if either path contains a single quote, since the
quoting used for those paths has no way to escape one.

A systemd `--user` manager only runs while a session for that user exists unless the account
has "lingering" enabled. Auto mode therefore chooses cron when lingering is off. To use
systemd-user timers while no one is logged in, enable lingering before installing them:

```bash
loginctl enable-linger "$(whoami)"
```

---

## Production Checklist

- [ ] MySQL database created with `utf8mb4` charset
- [ ] `backend/.env` configured with real credentials
- [ ] `./scripts/setup-instaloader.sh --configure-env --check` passes
- [ ] Authenticated Instaloader session stored on private persistent storage
- [ ] `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` set
- [ ] `APP_OWNER_EMAILS` populated (comma-separated)
- [ ] `FRONTEND_ORIGIN` matches production URL
- [ ] `SESSION_COOKIE_SECURE=true` set, and the proxy sends `X-Forwarded-Proto`
- [ ] `SPRING_PROFILES_ACTIVE` is unset (the `h2` profile is local-only and enables the H2 console)
- [ ] Google OAuth redirect URIs include production URL
- [ ] Frontend built with correct `VITE_API_BASE_URL`
- [ ] Reverse proxy serving HTTPS, forwarding `/api/**`, `/oauth2/**` and `/uploads/**`
- [ ] Backend running as systemd service
- [ ] Firewall allows ports 80 and 443 only
- [ ] Backend port 8080 not exposed publicly
- [ ] Database backups configured (`scripts/backup-mysql.sh`, installed periodically via
      `scripts/install-observability.sh`)
- [ ] Production health monitoring configured (`scripts/health-check.sh`, installed periodically
      via `scripts/install-observability.sh`)

### First-run steps

1. Create the MySQL tables and seed initial club data as described in
   [Database Setup](#database-setup) above (`SPRING_SQL_INIT_MODE=never` means the
   backend will not create them for you) — run `mvhs_clubs_seed.sql` against the new
   database
2. Deploy and start the backend
3. Open the home page — should show the club directory
4. Sign in as a platform owner (email in `APP_OWNER_EMAILS`)
5. Visit `/admin` — create clubs and manage the directory

---

## Troubleshooting

### Backend won't start

```bash
# Check logs
sudo journalctl -u hsclubs -n 50 --no-pager

# Common causes:
# - MySQL not running: systemctl status mysql
# - Wrong DB credentials: check .env
# - Port 8080 in use: lsof -i :8080
# - Google OAuth misconfig: check GOOGLE_CLIENT_ID
```

### Instaloader check fails

```bash
BACKEND_RUN_USER="${BACKEND_RUN_USER:-$(id -un)}"
./scripts/setup-instaloader.sh --configure-env --check
sudo -u "$BACKEND_RUN_USER" /opt/hsclubs/backend/.venv/bin/python -c 'import instaloader, browser_cookie3'
sudo journalctl -u hsclubs -n 100 --no-pager
```

If Python cannot create the virtual environment, install the distribution
packages listed in the Instaloader section. If imports work but avatars still
fall back to placeholders, recreate the saved session and verify that the
configured backend service account can read the configured session file.

### CORS errors in browser

Ensure `FRONTEND_ORIGIN` matches the actual frontend URL exactly (protocol + host + port).

### OAuth redirect mismatch

Google OAuth redirect URIs must match exactly:

- Dev: `http://localhost:8080/api/auth/google/callback`
- Prod: `https://yourdomain.com/api/auth/google/callback`
