# HSclubs — Deployment Guide

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

| Component | Minimum Version | Purpose |
|-----------|----------------|---------|
| Java | 17+ | Backend runtime |
| MySQL | 8.0+ | Database |
| Node.js | 20+ | Frontend build |
| npm | 10+ | Frontend dependencies |
| Nginx | 1.24+ | Reverse proxy (production) |
| Google Cloud Project | — | OAuth2 credentials |
| Domain | — | HTTPS + OAuth redirect |

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

### Frontend (`frontend/.env` or `.env.production`)

```bash
# Backend API base URL (defaults to http://localhost:8080)
VITE_API_BASE_URL=http://localhost:8080

# For production builds, set to your backend URL:
# VITE_API_BASE_URL=https://api.yourdomain.com
```

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
-- Check schools
SELECT id, slug, school_name, status FROM schools;

-- Check clubs with school context
SELECT c.name, s.school_name, c.status
FROM clubs c JOIN schools s ON c.school_id = s.id
LIMIT 5;

-- Check user-school relationships
SELECT ou.email, su.role, s.school_name
FROM school_users su
JOIN oauth_users ou ON ou.uid = su.oauth_user_id
JOIN schools s ON s.id = su.school_id;
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
ExecStart=/usr/bin/java -jar /opt/hsclubs/backend/backend-0.0.1-SNAPSHOT.jar
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

### Health check

```bash
# List schools (public endpoint)
curl http://localhost:8080/api/schools

# Check auth providers
curl http://localhost:8080/api/auth/providers
```

### Sizing guidelines

| School count | RAM | CPU |
|-------------|-----|-----|
| 1–10 | 512 MB | 1 vCPU |
| 10–50 | 1 GB | 2 vCPU |
| 50+ | 2 GB+ | 2–4 vCPU |

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
    root /opt/hsclubs/frontend/dist;
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
