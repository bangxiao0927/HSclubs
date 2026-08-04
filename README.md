# HSclubs - 1st Repo Sample School Platform

This repository is the first HSclubs repo: the sample school club directory that must be stable, useful, safe, and easy to maintain before any aggregator or mobile repo is created.

**Language:** All project content, documentation, and user-facing copy must be in English.

The short-term goal is not to split the product yet. The short-term goal is to make one school experience good enough that other schools can copy the pattern with confidence.

## Current Direction

Build the first repo into a complete school club workflow:

1. Students can open the school site and understand the club directory.
2. Students can search and browse clubs from a phone or desktop.
3. Students can view club details and apply to join.
4. Club presidents can manage their own club page and membership requests.
5. School admins can create clubs, assign presidents, and review school-level data.
6. The site keeps permissions, local development, image uploads, and account data simple enough for a volunteer school project to maintain.

## Repo Creation Strategy

The original planning note uses a three-repo path:

| Repo | Visibility | Purpose | Start Condition |
|------|------------|---------|-----------------|
| 1st repo | Public | Sample school site and core club workflow | Start now |
| 2nd repo | Public frontend, private backend | Multi-school summary and status collection | Start after the 1st repo has a stable summary API |
| 3rd repo | Public | Mobile app or mobile entry point for school switching | Start after the 1st repo works well on phones |

For now, all development should focus on the 1st repo. The 2nd and 3rd repos should stay as future boundaries, not active implementation targets.

More detail: [docs/REPO_STRATEGY.md](docs/REPO_STRATEGY.md)

## Priority Documents

- [docs/FIRST_REPO_ROADMAP.md](docs/FIRST_REPO_ROADMAP.md) - prioritized 1st repo work.
- [docs/DEVELOPMENT_SEQUENCE.md](docs/DEVELOPMENT_SEQUENCE.md) - short-cycle implementation order.
- [docs/API.md](docs/API.md) - current API reference.
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) - deployment and environment notes.

## Tech Stack

- Frontend: Vue 3, Vite, TypeScript, Pinia, Vue Router
- Backend: Spring Boot, MyBatis, Spring Security OAuth2
- Database: MySQL for normal development, H2 for tests

## Project Layout

```text
frontend/     Vue 3 + Vite application
backend/      Spring Boot API
docs/         Planning, deployment, API, and roadmap docs
scripts/      Data generation utilities
```

## Quick Start

Backend:

```bash
cd backend
cp .env.example .env   # fill in DB_PASSWORD and Google OAuth credentials
../scripts/setup-instaloader.sh --configure-env  # Linux: install and configure Instaloader
../scripts/setup-instaloader.sh --configure-env --check
./mvnw spring-boot:run
```

Instaloader must use an authenticated Instagram session; anonymous access is not supported because Instagram rejects it with `403 Forbidden`. On a headless Linux server, create a saved session as the backend service user and set `APP_INSTAGRAM_AVATAR_SESSION_USER` / `APP_INSTAGRAM_AVATAR_SESSION_FILE` in `backend/.env`. Browser-cookie support is installed for local Linux development. See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md#instaloader-avatar-cache) for distribution-specific prerequisites and session commands.
If Instaloader cannot resolve a profile picture, the backend also tries Instagram's web profile API before returning the short-lived SVG fallback.
The backend preloads avatars for clubs with an `instagramUrl`, stores them under `backend/uploads/avatar-cache/instagram`, and refreshes cached files after 60 days. The scheduler checks twice daily, but it only contacts Instagram for missing or expired files. Keep the upload directory and Instaloader session file on persistent, private storage in production.

Club post photos (the club media feed) are stored under `${app.upload.dir}/club-posts/`
(same upload root as the avatar cache, default `backend/uploads`). Like the avatar cache,
this directory must live on persistent, private storage in production: it is not backed up
by the database, and a nightly job only reclaims files no club post still references -- it
does not recreate anything lost.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Useful Checks

```bash
cd backend && ./mvnw test
cd frontend && npm run type-check
cd frontend && npm run test:unit
cd frontend && npm run build
```

## Maintainer Rule

Prefer the smallest useful feature that improves the sample school workflow. Avoid starting a new repo, large social features, or native mobile work until this repo is stable for real school users.

Additional rules for every update:

- Keep the repo focused on a single-school site. Other schools should copy the pattern instead of sharing a multi-tenant platform.
- After completing a PR (merging/resolving conflicts), open a follow-up commit to self-review the changes and verify PR status checks pass before marking the PR as ready.
- All user-facing and documentation copy must remain in English only.

## License

MIT
