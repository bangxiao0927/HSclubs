# HSclubs — Single-School Deployment Guide

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────┐
│   Browser    │────▶│   Nginx      │────▶│  Vue SPA │
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
| Node.js              | 20+                         | Frontend build             |
| npm                  | 10+                         | Frontend dependencies      |
| Python               | 3.10+ with `venv` and `pip` | Instaloader avatar cache   |
| Nginx                | 1.24+                       | Reverse proxy (production) |
| Google Cloud Project | —                           | OAuth2 credentials         |
| Domain               | —                           | HTTPS + OAuth redirect     |

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

Instaloader requires authentication. For a headless systemd host, create the
session as the same account that runs the backend so the service can read it:

```bash
sudo install -d -m 0700 -o hsclubs -g hsclubs /opt/hsclubs/backend/.instaloader
sudo -u hsclubs /opt/hsclubs/backend/.venv/bin/instaloader \
  --sessionfile /opt/hsclubs/backend/.instaloader/session-your_instagram_username \
  --login your_instagram_username
sudo chmod 0600 /opt/hsclubs/backend/.instaloader/session-your_instagram_username
```

Add the session identity and explicit file path to `backend/.env`:

```bash
APP_INSTAGRAM_AVATAR_SESSION_USER=your_instagram_username
APP_INSTAGRAM_AVATAR_SESSION_FILE=/opt/hsclubs/backend/.instaloader/session-your_instagram_username
```

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
VITE_CALENDAR_AFTER_SCHOOL_END=18:00
```

Vite embeds these values during `npm run build`. Rebuild and republish the frontend after changing them.

---

## Database Setup

### Option A: Fresh install with auto-init

Spring Boot runs `schema.sql` and `data.sql` on startup when `spring.sql.init.mode=always`. Create an empty schema and let the app bootstrap tables + seed data:

```sql
CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Start the backend — tables and initial MVHS data will be created automatically.

### Option B: Existing database (additive migration)

If you already have a running instance from an older version, apply the migration:

```bash
mysql -u root -p mydb < docs/schema-migration.sql
```

This script uses only `ALTER TABLE` and `CREATE TABLE IF NOT EXISTS` — safe to run multiple times.

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

Create `/etc/systemd/system/hsclubs.service`:

```ini
[Unit]
Description=HSclubs Backend
After=network.target mysql.service

[Service]
Type=simple
User=hsclubs
WorkingDirectory=/opt/hsclubs/backend
ExecStart=/usr/bin/java -jar /opt/hsclubs/backend/target/demo-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

# Pass environment variables
EnvironmentFile=/opt/hsclubs/backend/.env

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable hsclubs
sudo systemctl start hsclubs
sudo systemctl status hsclubs
```

The repository also includes `scripts/deploy-main.sh`, which initializes Instaloader when
the avatar cache is enabled, builds both applications, installs or updates the persistent
service, checks the backend, and then publishes the frontend. Its production defaults use
the system service and the `hsclubs` user:

```bash
./scripts/deploy-main.sh

# Skip Python initialization only when Instaloader is managed separately.
SETUP_INSTALOADER=0 ./scripts/deploy-main.sh
```

The deploy script respects `APP_INSTAGRAM_AVATAR_CACHE_ENABLED=false` and skips
Instaloader initialization when the cache is disabled.

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

### Nginx reverse proxy

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
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 callback passthrough
    location /oauth2/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
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

### SSL with Certbot

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

## Production Checklist

- [ ] MySQL database created with `utf8mb4` charset
- [ ] `backend/.env` configured with real credentials
- [ ] `./scripts/setup-instaloader.sh --configure-env --check` passes
- [ ] Authenticated Instaloader session stored on private persistent storage
- [ ] `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` set
- [ ] `APP_OWNER_EMAILS` populated (comma-separated)
- [ ] `FRONTEND_ORIGIN` matches production URL
- [ ] Google OAuth redirect URIs include production URL
- [ ] Frontend built with correct `VITE_API_BASE_URL`
- [ ] Nginx configured with SSL (certbot)
- [ ] Backend running as systemd service
- [ ] Firewall allows ports 80 and 443 only
- [ ] Backend port 8080 not exposed publicly
- [ ] Database backups configured

### First-run steps

1. Deploy and start the backend — tables auto-create
2. Open the home page — should show the club directory
3. Sign in as a platform owner (email in `APP_OWNER_EMAILS`)
4. Visit `/admin` — create clubs and manage the directory

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
./scripts/setup-instaloader.sh --configure-env --check
sudo -u hsclubs /opt/hsclubs/backend/.venv/bin/python -c 'import instaloader, browser_cookie3'
sudo journalctl -u hsclubs -n 100 --no-pager
```

If Python cannot create the virtual environment, install the distribution
packages listed in the Instaloader section. If imports work but avatars still
fall back to placeholders, recreate the saved session and verify that the
`hsclubs` service account can read the configured session file.

### CORS errors in browser

Ensure `FRONTEND_ORIGIN` matches the actual frontend URL exactly (protocol + host + port).

### OAuth redirect mismatch

Google OAuth redirect URIs must match exactly:

- Dev: `http://localhost:8080/api/auth/google/callback`
- Prod: `https://yourdomain.com/api/auth/google/callback`

### Invitation link not working

- Invitations expire after 7 days
- Each invitation can only be used once
- Recipient must be signed in with the invited email address
