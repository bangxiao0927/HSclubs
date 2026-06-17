# HSclubs — Multi‑School Club Directory Platform

A full‑stack platform where multiple schools can onboard, operate their own club directories, and let students explore, apply to, and manage extracurricular clubs.

## Tech Stack

- **Frontend:** Vue 3 + Vite + TypeScript + Pinia + Vue Router
- **Backend:** Spring Boot 4 + MyBatis + Spring Security OAuth2 (Google)
- **Database:** MySQL (H2 for tests)

## Project Layout

```
frontend/     Vue 3 + Vite application
backend/      Spring Boot API
docs/         Planning, migration SQL, API reference
scripts/      Data generation utilities
```

## Quick Start

### Prerequisites

- Java 17+
- MySQL 8+
- Node.js 20+ with npm
- Google OAuth2 credentials

### Backend

```bash
cd backend

# Configure environment
cp .env.example .env   # set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, DB creds

# Start (creates tables + seeds data)
./mvnw spring-boot:run     # http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev                  # http://localhost:4173
```

## Multi‑School Architecture

```
┌──────────────────────────────────┐
│          Platform Layer           │
│  /platform/admin · school CRUD    │
└────────────┬─────────────────────┘
             │
   ┌─────────┼─────────┐
   ▼         ▼         ▼
School A  School B  School C
   │
   ├── /schools/{slug}/clubs
   ├── /schools/{slug}/calendar
   ├── /schools/{slug}/admin
   └── /schools/{slug}/profile
```

### Roles

| Role | Scope | Capabilities |
|------|-------|-------------|
| Platform Owner | Global | Create/manage schools, view all data |
| School Admin | Per school | Manage clubs, review applications |
| Club President | Per club | Edit club info, manage members |
| Student | Per school | Browse, apply to clubs |

### School Onboarding

1. Platform Owner creates a school via `/platform/admin`
2. Students sign in with Google OAuth
3. Students are assigned to their school via `school_users`
4. School Admins manage clubs independently

## API Overview

Full reference: `docs/API.md`

```
GET    /api/schools                        List active schools
GET    /api/schools/{slug}                 School detail + branding

GET    /api/schools/{slug}/clubs           List school's clubs
POST   /api/schools/{slug}/clubs           Create club [school_admin]
GET    /api/schools/{slug}/clubs/{id}      Club detail + viewer status
PUT    /api/schools/{slug}/clubs/{id}      Update club [admin|president]
DELETE /api/schools/{slug}/clubs/{id}      Delete club [school_admin]

POST   /api/schools/{slug}/clubs/{id}/members/apply     Apply to join
DELETE /api/schools/{slug}/clubs/{id}/members/apply     Cancel application

GET    /api/schools/{slug}/clubs/{id}/membership-requests        Pending list
POST   .../membership-requests/{reqId}/approve                  Approve
DELETE .../membership-requests/{reqId}                           Reject

GET    /api/auth/providers                  OAuth providers
GET    /api/auth/me                         Current user + school memberships
POST   /api/auth/logout                     Sign out

GET    /api/platform/schools                [platform_owner] All schools
POST   /api/platform/schools                [platform_owner] Create school
PUT    /api/platform/schools/{slug}         [platform_owner] Update school
```

## Useful Scripts

```bash
# Backend tests
cd backend && ./mvnw test

# Frontend type-check
cd frontend && npm run type-check

# Frontend lint
cd frontend && npm run lint

# Frontend unit tests
cd frontend && npm run test:unit

# Production build
cd frontend && npm run build
```

## Configuration

| Env Variable | Purpose | Default |
|-------------|---------|--------|
| `GOOGLE_CLIENT_ID` | Google OAuth client ID | — |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret | — |
| `APP_OWNER_EMAILS` | Comma-separated platform owner emails | — |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/mydb` |
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | — |
| `FRONTEND_ORIGIN` | CORS allowed origin | `http://localhost:4173` |
| `VITE_API_BASE_URL` | Backend base URL (frontend) | `http://localhost:8080` |

## License

MIT
