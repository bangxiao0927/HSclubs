# HSclubs

## Project layout

- `backend/` – Spring Boot service (existing backend).
- `frontend/` – Vue 3 + Vite application scaffolded with TypeScript, Vue Router, Pinia, Vitest, and ESLint + Prettier.

## Frontend quick start

```bash
cd frontend
npm install             # install deps (Node 18+ recommended; project tested with Node 24 via nvm)
npm run dev
npm run preview            # start Vite dev server on http://localhost:4173 (override via FRONTEND_PORT)
```

### Other useful scripts

```bash
npm run build           # production build output to dist/
npm run test:unit       # run Vitest unit tests
npm run lint            # ESLint + Prettier checks
```

## Backend + database quick start

```bash
cd backend
./mvnw spring-boot:run    # starts the Spring Boot API on http://localhost:8080
```

The backend expects a MySQL instance (see `backend/src/main/resources/application.yaml`).
On startup Spring Boot will create/seed `schools` and `clubs` tables (via `schema.sql`/`data.sql`), so an
empty `mydb` schema is enough. Every club row references a valid school through `schoolId`.
Once the service is running you can hit:

- `GET    http://localhost:8080/api/clubs` – list all clubs from MySQL.
- `GET    http://localhost:8080/api/clubs/{id}` – fetch a single club.
- `POST   http://localhost:8080/api/clubs` – create a club (JSON body, id omitted, include `schoolId`).
- `PUT    http://localhost:8080/api/clubs/{id}` – update a club (include `schoolId`).
- `DELETE http://localhost:8080/api/clubs/{id}` – delete a club.

### OAuth2 endpoints

The backend now exposes an initial OAuth 2.0 surface backed by Google sign-in.

- `GET  http://localhost:8080/api/auth/providers` – discover available OAuth providers and their authorization URLs.
- `GET  http://localhost:8080/api/auth/me` – return the currently authenticated user (401 when not logged in).
- `POST http://localhost:8080/api/auth/logout` – invalidate the Spring Security session cookie.

Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (see `backend/.env` for local development) before starting the backend so Spring Security can complete the OAuth flow. The current configuration expects Google to redirect to `http://localhost:8080/api/auth/google/callback`, matching the OAuth client you shared.

The Vue dev server is already allowed via CORS (`FRONTEND_ORIGIN`, default `http://localhost:4173`), so the frontend can
call the backend without extra proxying.

Set `FRONTEND_PORT` or `FRONTEND_ORIGIN` (default redirect is `${FRONTEND_ORIGIN}/auth/callback`) if you need the backend to send users somewhere else after OAuth success/failure. On the frontend side, `VITE_API_BASE_URL` is optional now—dev builds automatically fall back to `http://localhost:8080`, but you can override it for other environments.
