# HSclubs

## Project layout

- `backend/` – Spring Boot service (existing backend).
- `frontend/` – Vue 3 + Vite application scaffolded with TypeScript, Vue Router, Pinia, Vitest, and ESLint + Prettier.

## Frontend quick start

```bash
cd frontend
npm install             # install deps (Node 18+ recommended; project tested with Node 24 via nvm)
npm run dev             # start Vite dev server on http://localhost:5173
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

The Vue dev server is already allowed via CORS (`http://localhost:5173`), so the frontend can
call the backend without extra proxying.
