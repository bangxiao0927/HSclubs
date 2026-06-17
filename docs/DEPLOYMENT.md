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
