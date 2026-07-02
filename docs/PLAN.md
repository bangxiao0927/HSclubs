# HSclubs - Long-Term Multi-School Platform Plan

> Status: reference material. For current active work, start with README.md, docs/REPO_STRATEGY.md, docs/FIRST_REPO_ROADMAP.md, and docs/DEVELOPMENT_SEQUENCE.md. This file preserves the broader platform plan, but it is not the current execution order for the 1st repo.


## 1. Vision

HSclubs evolves from a single‑school (Mountain View High School) club directory into a
**multi‑school platform** where any school can onboard, manage its own club directory,
and let its students/admins operate independently — all within one unified system.

### 1.1 Core goals

- Any school can join the platform and get its own branded club directory.
- Each school operates autonomously: own clubs, own members, own admins.
- Students can belong to multiple schools (e.g. home school + summer programme).
- Platform owners retain global oversight without interfering in day‑to‑day school ops.
- The existing MVHS data becomes the first school on the platform — not a throwaway seed.

### 1.2 Non‑goals (for now)

- Per‑school custom domains / white‑label deployments.
- Paid tiers or subscription billing.
- Public API for third‑party integrations.
- Mobile native apps.

---

## 2. Target Architecture

```
┌──────────────────────────────────────────────┐
│                 Platform Layer                │
│  School onboarding · global auth · platform   │
│  owner dashboard · school directory           │
└──────────────────┬───────────────────────────┘
                   │
     ┌─────────────┼─────────────┐
     ▼             ▼             ▼
┌─────────┐  ┌─────────┐  ┌─────────┐
│ School A │  │ School B │  │ School C │
│ clubs    │  │ clubs    │  │ clubs    │
│ members  │  │ members  │  │ members  │
│ admins   │  │ admins   │  │ admins   │
└─────────┘  └─────────┘  └─────────┘
```

### 2.1 Three conceptual layers

| Layer | Responsibility | Examples |
|-------|---------------|----------|
| Platform | School lifecycle, global auth, platform owners | Create school, suspend school, view all schools |
| School | Club directory, school branding, school admins | Manage clubs, review applications, configure school page |
| Club | Club data, members, membership requests | Edit club info, approve/reject members, roster management |

### 2.2 Tech stack (unchanged)

- **Frontend:** Vue 3 + Vite + TypeScript + Pinia + Vue Router
- **Backend:** Spring Boot 4 + MyBatis + Spring Security OAuth2
- **Database:** MySQL (H2 for tests)
- **Auth:** Google OAuth2 (extensible to more providers)

---

## 3. Database Design

### 3.1 Entity‑Relationship overview

```
oauth_users ──┬── user_profiles
              │
              ├── school_users ──── schools
              │
              ├── club_member ───── clubs ──── schools
              │
              └── club_membership_requests ── clubs
```

### 3.2 Migration SQL (additive — does NOT drop existing tables)

```sql
-- ============================================================
-- Phase 1: Extend schools into a first‑class entity
-- ============================================================
ALTER TABLE schools
  ADD COLUMN slug         VARCHAR(80)  NOT NULL UNIQUE AFTER id,
  ADD COLUMN short_name   VARCHAR(120) NULL     AFTER school_name,
  ADD COLUMN logo_url     VARCHAR(500) NULL     AFTER short_name,
  ADD COLUMN banner_url   VARCHAR(500) NULL     AFTER logo_url,
  ADD COLUMN primary_color VARCHAR(20) NULL     AFTER banner_url,
  ADD COLUMN school_domain VARCHAR(160) NULL    AFTER primary_color,
  ADD COLUMN timezone     VARCHAR(80)  NOT NULL DEFAULT 'America/Los_Angeles'
                                              AFTER school_domain,
  ADD COLUMN status       VARCHAR(30)  NOT NULL DEFAULT 'active'
                                              AFTER timezone,
  ADD COLUMN created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              AFTER status,
  ADD COLUMN updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP
                                              AFTER created_at;

CREATE INDEX idx_schools_status  ON schools(status);
CREATE UNIQUE INDEX uq_schools_domain ON schools(school_domain);

-- Seed: give MVHS a slug and branding
UPDATE schools
SET slug = 'mvhs',
    short_name = 'MVHS',
    status = 'active'
WHERE id = 1;

-- ============================================================
-- Phase 2: user_profiles — add home school
-- ============================================================
ALTER TABLE user_profiles
  ADD COLUMN home_school_id BIGINT NULL AFTER graduation_year,
  ADD CONSTRAINT fk_user_profiles_home_school
    FOREIGN KEY (home_school_id) REFERENCES schools(id)
    ON DELETE SET NULL;

CREATE INDEX idx_user_profiles_home_school_id
  ON user_profiles(home_school_id);

-- ============================================================
-- Phase 3: school_users — user↔school relationship
-- ============================================================
CREATE TABLE IF NOT EXISTS school_users (
  id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id               BIGINT      NOT NULL,
  oauth_user_id           BIGINT      NOT NULL,
  role                    VARCHAR(50) NOT NULL DEFAULT 'student',
  status                  VARCHAR(30) NOT NULL DEFAULT 'active',
  joined_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_by_oauth_user_id BIGINT     NULL,

  UNIQUE KEY uq_school_user (school_id, oauth_user_id),

  CONSTRAINT fk_school_users_school
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
  CONSTRAINT fk_school_users_user
    FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE,
  CONSTRAINT fk_school_users_invited_by
    FOREIGN KEY (invited_by_oauth_user_id) REFERENCES oauth_users(uid)
    ON DELETE SET NULL
);

CREATE INDEX idx_school_users_role   ON school_users(role);
CREATE INDEX idx_school_users_status ON school_users(status);

-- ============================================================
-- Phase 4: clubs — add slug, status, visibility, approval
-- ============================================================
ALTER TABLE clubs
  ADD COLUMN slug                    VARCHAR(160) NULL AFTER name,
  ADD COLUMN status                  VARCHAR(30)  NOT NULL DEFAULT 'active'
                                                   AFTER school_id,
  ADD COLUMN visibility              VARCHAR(30)  NOT NULL DEFAULT 'public'
                                                   AFTER status,
  ADD COLUMN approved_at             TIMESTAMP    NULL AFTER visibility,
  ADD COLUMN approved_by_oauth_user_id BIGINT     NULL AFTER approved_at,

  ADD CONSTRAINT fk_clubs_approved_by
    FOREIGN KEY (approved_by_oauth_user_id)
    REFERENCES oauth_users(uid) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_clubs_school_slug
  ON clubs(school_id, slug);
CREATE INDEX idx_clubs_school_status
  ON clubs(school_id, status);

-- ============================================================
-- Phase 5: club_member — add joined_at
-- ============================================================
ALTER TABLE club_member
  ADD COLUMN joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                       AFTER role_name;

CREATE INDEX idx_club_member_user ON club_member(oauth_user_id);

-- ============================================================
-- Phase 6: club_membership_requests — add review fields
-- ============================================================
ALTER TABLE club_membership_requests
  ADD COLUMN status                  VARCHAR(30) NOT NULL DEFAULT 'pending'
                                                AFTER created_at,
  ADD COLUMN reviewed_at             TIMESTAMP   NULL AFTER status,
  ADD COLUMN reviewed_by_oauth_user_id BIGINT    NULL AFTER reviewed_at,
  ADD COLUMN note                    VARCHAR(500) NULL
                                                AFTER reviewed_by_oauth_user_id,

  ADD CONSTRAINT fk_membership_requests_reviewed_by
    FOREIGN KEY (reviewed_by_oauth_user_id)
    REFERENCES oauth_users(uid) ON DELETE SET NULL;

CREATE INDEX idx_membership_requests_status
  ON club_membership_requests(status);

-- ============================================================
-- Phase 7: school_admin_invitations (optional, for Phase 5+)
-- ============================================================
CREATE TABLE IF NOT EXISTS school_admin_invitations (
  id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id               BIGINT       NOT NULL,
  email                   VARCHAR(200) NOT NULL,
  role                    VARCHAR(50)  NOT NULL DEFAULT 'school_admin',
  status                  VARCHAR(30)  NOT NULL DEFAULT 'pending',
  token                   VARCHAR(255) NOT NULL,
  expires_at              TIMESTAMP    NOT NULL,
  created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  invited_by_oauth_user_id BIGINT      NULL,

  UNIQUE KEY uq_school_invite_token (token),
  UNIQUE KEY uq_school_invite_email (school_id, email, status),

  CONSTRAINT fk_school_admin_invites_school
    FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
  CONSTRAINT fk_school_admin_invites_invited_by
    FOREIGN KEY (invited_by_oauth_user_id) REFERENCES oauth_users(uid)
    ON DELETE SET NULL
);
```

### 3.3 Enumerated values

| Table | Column | Allowed values |
|-------|--------|---------------|
| schools | status | `active`, `pending`, `archived` |
| school_users | role | `student`, `school_admin`, `school_staff` |
| school_users | status | `active`, `pending`, `invited`, `disabled` |
| clubs | status | `draft`, `active`, `archived` |
| clubs | visibility | `public`, `school_only` |
| club_member | role_name | `member`, `officer`, `president`, `advisor` |
| club_membership_requests | status | `pending`, `approved`, `rejected`, `cancelled` |

---

## 4. API Design

### 4.1 Endpoint map

```
Platform endpoints
  GET    /api/schools                          → list all active schools
  GET    /api/schools/{slug}                   → single school detail + branding
  GET    /api/platform/schools                 → [platform_owner] all schools incl. pending
  POST   /api/platform/schools                 → [platform_owner] create school
  PUT    /api/platform/schools/{slug}          → [platform_owner] update school
  POST   /api/platform/schools/{slug}/admins/invitations
                                               → [platform_owner] invite school admin

School‑scoped club endpoints
  GET    /api/schools/{schoolSlug}/clubs       → list clubs for school
  POST   /api/schools/{schoolSlug}/clubs       → [school_admin] create club
  GET    /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
                                               → single club detail + viewer permissions
  PUT    /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
                                               → [school_admin|club_president] update
  DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
                                               → [school_admin] archive/delete

School‑scoped membership endpoints
  GET    /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members
                                               → [school_admin|club_president] member list
  POST   /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members/apply
                                               → [authenticated] apply to join
  DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members/apply
                                               → [authenticated] cancel own application

School‑scoped request management
  GET    /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/membership-requests
                                               → [school_admin|club_president] pending list
  POST   /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/membership-requests/{requestId}/approve
                                               → [school_admin|club_president] approve
  DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/membership-requests/{requestId}
                                               → [school_admin|club_president] reject

School admin dashboard
  GET    /api/schools/{schoolSlug}/admin/stats
                                               → [school_admin] summary stats
  GET    /api/schools/{schoolSlug}/admin/clubs
                                               → [school_admin] all clubs (incl. draft)

Auth + user endpoints (enhanced)
  GET    /api/auth/providers                   → OAuth providers
  GET    /api/auth/me                          → current user + school memberships
  POST   /api/auth/logout                      → invalidate session
  PATCH  /api/users/me/home-school             → set default school
  GET    /api/users/me/schools                 → list user's school memberships
  PATCH  /api/users/me/graduation-year         → update graduation year
```

### 4.2 Migration strategy

Existing endpoints are preserved during Phase 2 and deprecated in Phase 4.

| Current endpoint | Replaced by | Removed in |
|-----------------|-------------|------------|
| `GET /api/clubs` | `GET /api/schools/{schoolSlug}/clubs` | Phase 4 |
| `GET /api/clubs/{id}` | `GET /api/schools/{schoolSlug}/clubs/{clubSlugOrId}` | Phase 4 |
| `POST /api/clubs` | `POST /api/schools/{schoolSlug}/clubs` | Phase 4 |
| `PUT /api/clubs/{id}` | `PUT /api/schools/{schoolSlug}/clubs/{clubSlugOrId}` | Phase 4 |
| `DELETE /api/clubs/{id}` | `DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}` | Phase 4 |
| `GET /api/clubs/{id}/members` | school‑scoped equivalent | Phase 4 |
| `POST /api/clubs/{id}/members/apply` | school‑scoped equivalent | Phase 4 |
| `DELETE /api/clubs/{id}/members/apply` | school‑scoped equivalent | Phase 4 |
| `GET /api/clubs/{id}/membership-requests` | school‑scoped equivalent | Phase 4 |
| `POST /api/clubs/{id}/membership-requests/{reqId}/approve` | school‑scoped | Phase 4 |
| `DELETE /api/clubs/{id}/membership-requests/{reqId}` | school‑scoped | Phase 4 |

### 4.3 Key response shape: `GET /api/auth/me`

```json
{
  "id": "google-123",
  "email": "maya.chen@example.com",
  "displayName": "Maya Chen",
  "avatarUrl": "https://...",
  "provider": "google",
  "graduationYear": 2026,
  "isPlatformOwner": false,
  "homeSchool": {
    "schoolId": 1,
    "slug": "mvhs",
    "schoolName": "Mountain View High School"
  },
  "schoolMemberships": [
    {
      "schoolId": 1,
      "slug": "mvhs",
      "schoolName": "Mountain View High School",
      "role": "school_admin",
      "status": "active"
    }
  ]
}
```

---

## 5. Frontend Architecture

### 5.1 Route tree (target)

```
/                                           → platform home / school discovery
/schools                                    → school directory page
/schools/:schoolSlug                        → school home (club directory for this school)
/schools/:schoolSlug/search                 → school‑scoped club search
/schools/:schoolSlug/calendar               → school‑scoped weekly calendar
/schools/:schoolSlug/categories             → school‑scoped category browser
/schools/:schoolSlug/clubs/:clubSlugOrId    → club detail
/schools/:schoolSlug/clubs/:clubSlugOrId/admin
                                            → club admin (edit + roster)
/schools/:schoolSlug/clubs/:clubSlugOrId/admin/pending
                                            → membership request review
/schools/:schoolSlug/admin                  → school admin dashboard
/schools/:schoolSlug/profile                → user profile (school context)

/auth                                        → sign‑in / provider selection
/auth/callback                               → OAuth callback handler
/platform/admin                              → platform owner dashboard
```

### 5.2 New components

| Component | Purpose |
|-----------|---------|
| `SchoolSelector.vue` | Dropdown/switcher in header when user belongs to multiple schools |
| `SchoolHomeView.vue` | School landing page with club directory |
| `PlatformAdminView.vue` | Platform owner: create/manage schools |
| `SchoolPickerView.vue` | Public school discovery page |

### 5.3 New stores

| Store | Key state |
|-------|----------|
| `school.ts` | `currentSchool`, `availableSchools`, `userSchoolMemberships`, `setCurrentSchool(slug)` |

### 5.4 New services

| Service | Endpoints |
|---------|----------|
| `schoolService.ts` | `fetchSchools()`, `fetchSchoolBySlug(slug)` |
| `schoolAdminService.ts` | `fetchSchoolStats(slug)`, `fetchSchoolClubs(slug)`, `createClub(slug, data)` |

### 5.5 Modified services

All methods in `clubService.ts` gain a `schoolSlug` parameter:

```ts
fetchClubs(schoolSlug: string, options?: FetchClubsOptions)
fetchClubById(schoolSlug: string, clubIdOrSlug: string | number)
updateClub(schoolSlug: string, clubIdOrSlug: string | number, data: Partial<Club>)
fetchClubMembers(schoolSlug: string, clubIdOrSlug: string | number)
applyToClub(schoolSlug: string, clubIdOrSlug: string | number)
cancelMembershipRequest(schoolSlug: string, clubIdOrSlug: string | number)
fetchMembershipRequests(schoolSlug: string, clubIdOrSlug: string | number)
approveMembershipRequest(schoolSlug: string, clubIdOrSlug: string | number, requestId: string | number)
rejectMembershipRequest(schoolSlug: string, clubIdOrSlug: string | number, requestId: string | number)
```

---

## 6. Role & Permission Matrix

| Action | Guest | Student | Club President | School Admin | Platform Owner |
|--------|-------|---------|---------------|-------------|----------------|
| View school list | ✓ | ✓ | ✓ | ✓ | ✓ |
| Browse school clubs | ✓ | ✓ | ✓ | ✓ | ✓ |
| View club detail | ✓ | ✓ | ✓ | ✓ | ✓ |
| Sign in (OAuth) | → becomes Student | — | — | — | — |
| Apply to club | — | ✓ | ✓ | ✓ | ✓ |
| Cancel own application | — | ✓ | ✓ | ✓ | ✓ |
| Edit own club (president of) | — | — | ✓ | ✓ | ✓ |
| View club roster | — | — | ✓ | ✓ | ✓ |
| Approve/reject club applications | — | — | ✓ | ✓ | ✓ |
| Create club in school | — | — | — | ✓ | ✓ |
| Archive/delete club in school | — | — | — | ✓ | ✓ |
| View school stats | — | — | — | ✓ | ✓ |
| Manage school admins | — | — | — | — | ✓ |
| Create/archive schools | — | — | — | — | ✓ |
| View all schools (incl. pending) | — | — | — | — | ✓ |

### 6.1 Backend permission helpers (to implement)

```java
// In a shared security helper or base controller
void requirePlatformOwner(Authentication auth);
School requireSchoolAdmin(String schoolSlug, Authentication auth);
Club requireClubManager(String schoolSlug, String clubSlug, Authentication auth);
```

---

## 7. Development Phases

### Phase 1 — Database foundation (Week 1)

**Goal:** Schools become first‑class entities with slugs. No API or UI changes yet.

| # | Task | Files |
|---|------|-------|
| 1.1 | Write migration SQL for `schools` (slug, branding, status) | `docs/schema-migration.sql` |
| 1.2 | Write migration SQL for `school_users` | same file |
| 1.3 | Write migration SQL for `clubs` (slug, status) | same file |
| 1.4 | Write migration SQL for `club_membership_requests` (review fields) | same file |
| 1.5 | Update `data.sql` seed: give MVHS slug + branding | `backend/src/main/resources/data.sql` |
| 1.6 | Update `schema.sql` to reflect new columns | `backend/src/main/resources/schema.sql` |
| 1.7 | Extend `School` model with new fields | `backend/.../school/model/School.java` |
| 1.8 | Extend `SchoolMapper` + `SchoolMapper.xml` with new columns | mapper files |
| 1.9 | Extend `Club` model with `slug`/`status`/`visibility` | `backend/.../club/model/Club.java` |
| 1.10 | Extend `ClubMapper` + `ClubMapper.xml` with new columns | mapper files |
| 1.11 | Create `SchoolUser` model | `backend/.../school/model/SchoolUser.java` |
| 1.12 | Create `SchoolUserMapper` + XML | mapper files |
| 1.13 | Run backend tests; verify schema migration applies cleanly | — |

### Phase 2 — School API + existing API coexistence (Week 2–3)

**Goal:** School‑scoped club endpoints live alongside old ones. Frontend still uses old endpoints.

| # | Task | Files |
|---|------|-------|
| 2.1 | Create `SchoolService` (findAllActive, findBySlug) | `backend/.../school/service/SchoolService.java` |
| 2.2 | Create `SchoolController` (GET /api/schools, GET /api/schools/{slug}) | `backend/.../school/controller/SchoolController.java` |
| 2.3 | Add `findAllBySchoolId`, `findBySchoolIdAndSlug` to `ClubMapper` + XML | mapper files |
| 2.4 | Add `findAllBySchoolSlug`, `findBySchoolAndClubSlug` to `ClubService` | `backend/.../club/service/ClubService.java` |
| 2.5 | Create `SchoolClubController` with school‑scoped endpoints | `backend/.../club/controller/SchoolClubController.java` |
| 2.6 | Add `schoolMemberships` to `GET /api/auth/me` response | `AuthService.java`, `AuthUser.java` |
| 2.7 | Add `GET /api/users/me/schools`, `PATCH /api/users/me/home-school` | `UserController.java` |
| 2.8 | Add `SchoolUserService` for school membership CRUD | `backend/.../school/service/SchoolUserService.java` |
| 2.9 | Write backend integration tests for school‑scoped club endpoints | `backend/src/test/.../club/` |
| 2.10 | Write backend tests for school membership in auth response | `backend/src/test/.../auth/` |

### Phase 3 — Frontend school layer (Week 3–4)

**Goal:** Frontend gains school context. URLs change. Old views adapt.

| # | Task | Files |
|---|------|-------|
| 3.1 | Add school‑scoped routes to Vue Router | `frontend/src/router/index.ts` |
| 3.2 | Create `schoolService.ts` | `frontend/src/services/schoolService.ts` |
| 3.3 | Create `school.ts` Pinia store | `frontend/src/stores/school.ts` |
| 3.4 | Create `SchoolPickerView.vue` (school discovery page) | `frontend/src/views/SchoolPickerView.vue` |
| 3.5 | Adapt `HomeView.vue` → school‑scoped club listing | `frontend/src/views/HomeView.vue` |
| 3.6 | Adapt `AboutView.vue` → school‑scoped category browser | `frontend/src/views/AboutView.vue` |
| 3.7 | Adapt `ClubSearchView.vue` → school‑scoped search | `frontend/src/views/ClubSearchView.vue` |
| 3.8 | Adapt `CalendarView.vue` → school‑scoped calendar | `frontend/src/views/CalendarView.vue` |
| 3.9 | Adapt `ClubDetailView.vue` → school‑scoped club detail | `frontend/src/views/ClubDetailView.vue` |
| 3.10 | Adapt `ClubAdminView.vue` → school‑scoped club admin | `frontend/src/views/ClubAdminView.vue` |
| 3.11 | Adapt `ClubPendingView.vue` → school‑scoped request review | `frontend/src/views/ClubPendingView.vue` |
| 3.12 | Adapt `ProfileView.vue` → show school context | `frontend/src/views/ProfileView.vue` |
| 3.13 | Adapt `OwnerAdminView.vue` → school admin dashboard | `frontend/src/views/OwnerAdminView.vue` |
| 3.14 | Add `SchoolSelector.vue` to `App.vue` header (when user has >1 school) | `frontend/src/App.vue`, `frontend/src/components/SchoolSelector.vue` |
| 3.15 | Update `clubService.ts` — all methods accept `schoolSlug` | `frontend/src/services/clubService.ts` |
| 3.16 | Update `auth.ts` store — read `homeSchool`, `schoolMemberships` | `frontend/src/stores/auth.ts` |
| 3.17 | Write frontend unit tests for school store + school service | `frontend/src/stores/__tests__/`, `frontend/src/services/__tests__/` |

### Phase 4 — Deprecate old global endpoints (Week 5)

**Goal:** Remove or redirect old `/api/clubs` endpoints. All traffic goes through school‑scoped endpoints.

| # | Task |
|---|------|
| 4.1 | Add redirect/forward from old endpoints to school‑scoped ones (or return 410) |
| 4.2 | Remove old endpoint methods from `ClubController` |
| 4.3 | Remove `ClubService.findAll()` (global) |
| 4.4 | Verify all frontend calls use new endpoints |
| 4.5 | Update README with new API docs |

### Phase 5 — Platform admin + school onboarding (Week 5–6)

**Goal:** Platform owners can create/manage schools. School admins can be invited.

| # | Task | Files |
|---|------|-------|
| 5.1 | Create `PlatformAdminController` | `backend/.../platform/controller/PlatformAdminController.java` |
| 5.2 | Implement `POST /api/platform/schools` (create school) | controller + service |
| 5.3 | Implement `PUT /api/platform/schools/{slug}` (update school) | controller + service |
| 5.4 | Implement school admin invitation flow | `SchoolAdminInvitationService.java` |
| 5.5 | Create `PlatformAdminView.vue` | `frontend/src/views/PlatformAdminView.vue` |
| 5.6 | Create `platformAdminService.ts` | `frontend/src/services/platformAdminService.ts` |
| 5.7 | Add invitation acceptance page | `frontend/src/views/AcceptInvitationView.vue` |
| 5.8 | Write backend tests for platform admin endpoints | test files |

### Phase 6 — Polish, testing, docs (Week 6–7)

**Goal:** Hardening, cleanup, documentation.

| # | Task |
|---|------|
| 6.1 | Remove unused scaffold components (`HelloWorld`, `TheWelcome`, `WelcomeItem`, `Icon*`, `counter.ts`) |
| 6.2 | Add school‑isolation integration tests (user from school A cannot see school B data) |
| 6.3 | Add permission‑denied tests (student cannot edit club; non‑admin cannot see roster) |
| 6.4 | Add end‑to‑end test: sign in → pick school → browse clubs → apply → president approves |
| 6.5 | Update `README.md` with multi‑school architecture + quickstart |
| 6.6 | Add `docs/API.md` with full endpoint reference |
| 6.7 | Add `docs/DEPLOYMENT.md` with environment variable reference |
| 6.8 | Performance: add pagination to club listing endpoints |
| 6.9 | UI polish: loading skeletons, empty states, error boundaries |

---

## 8. Files to Create

| File | Purpose |
|------|---------|
| `docs/schema-migration.sql` | Migration SQL for Phases 1–6 |
| `docs/API.md` | API reference |
| `backend/.../school/model/SchoolUser.java` | School user relationship model |
| `backend/.../school/mapper/SchoolUserMapper.java` | School user mapper interface |
| `backend/.../school/mapper/SchoolUserMapper.xml` | School user mapper XML |
| `backend/.../school/service/SchoolService.java` | School CRUD + lookup |
| `backend/.../school/service/SchoolUserService.java` | School membership management |
| `backend/.../school/controller/SchoolController.java` | Public school endpoints |
| `backend/.../school/controller/SchoolAdminController.java` | School admin dashboard endpoints |
| `backend/.../club/controller/SchoolClubController.java` | School‑scoped club endpoints |
| `backend/.../platform/controller/PlatformAdminController.java` | Platform owner endpoints |
| `backend/.../platform/service/PlatformService.java` | Platform‑level operations |
| `frontend/src/stores/school.ts` | Current school Pinia store |
| `frontend/src/services/schoolService.ts` | School API client |
| `frontend/src/services/schoolAdminService.ts` | School admin API client |
| `frontend/src/services/platformAdminService.ts` | Platform admin API client |
| `frontend/src/views/SchoolPickerView.vue` | School discovery page |
| `frontend/src/views/PlatformAdminView.vue` | Platform owner dashboard |
| `frontend/src/views/AcceptInvitationView.vue` | Invitation acceptance page |
| `frontend/src/components/SchoolSelector.vue` | School switcher dropdown |

## 9. Files to Modify

| File | Change |
|------|--------|
| `backend/.../school/model/School.java` | Add slug, shortName, logoUrl, bannerUrl, primaryColor, status, timestamps |
| `backend/.../school/mapper/SchoolMapper.java` | Add findBySlug, findAllActive, insert, update |
| `backend/.../school/mapper/SchoolMapper.xml` | Add corresponding SQL |
| `backend/.../club/model/Club.java` | Add slug, status, visibility, approvedAt, approvedBy |
| `backend/.../club/mapper/ClubMapper.java` | Add findAllBySchoolId, findBySchoolIdAndSlug |
| `backend/.../club/mapper/ClubMapper.xml` | Add corresponding SQL |
| `backend/.../club/service/ClubService.java` | Add school‑scoped methods; keep old ones temporarily |
| `backend/.../club/controller/ClubController.java` | Mark old endpoints deprecated; eventually remove |
| `backend/.../auth/model/AuthUser.java` | Add homeSchool, schoolMemberships |
| `backend/.../auth/service/AuthService.java` | Populate school context in getAuthenticatedUser |
| `backend/.../user/controller/UserController.java` | Add home‑school + school list endpoints |
| `backend/src/main/resources/schema.sql` | Add new columns/tables |
| `backend/src/main/resources/data.sql` | Add MVHS slug + sample school_users |
| `frontend/src/router/index.ts` | Add school‑scoped routes + platform routes |
| `frontend/src/services/clubService.ts` | Add schoolSlug to all methods |
| `frontend/src/stores/auth.ts` | Parse homeSchool + schoolMemberships from /me |
| `frontend/src/types/club.ts` | Add slug, status, visibility |
| `frontend/src/types/auth.ts` | Add homeSchool, SchoolMembership |
| `frontend/src/App.vue` | Add SchoolSelector; update nav links to school‑scoped |
| `frontend/src/views/HomeView.vue` | Fetch clubs by school; update header copy |
| `frontend/src/views/AboutView.vue` | Scope category browser to school |
| `frontend/src/views/CalendarView.vue` | Scope calendar to school |
| `frontend/src/views/ClubSearchView.vue` | Scope search to school |
| `frontend/src/views/ClubDetailView.vue` | Use school‑scoped endpoints |
| `frontend/src/views/ClubAdminView.vue` | Use school‑scoped endpoints |
| `frontend/src/views/ClubPendingView.vue` | Use school‑scoped endpoints |
| `frontend/src/views/ProfileView.vue` | Show school context + membership list |
| `frontend/src/views/OwnerAdminView.vue` | Become school admin dashboard |
| `frontend/src/views/AuthChoiceView.vue` | Update redirect to land on school home |
| `README.md` | Update with multi‑school architecture |

---

## 10. Testing Strategy

### 10.1 Backend tests (JUnit + Mockito + H2)

| Priority | Test | What it verifies |
|----------|------|-----------------|
| P0 | `SchoolClubControllerTest` | `GET /schools/{slug}/clubs` returns only that school's clubs |
| P0 | `SchoolClubControllerTest` | `POST /clubs` as student → 403 |
| P0 | `MembershipFlowTest` | apply → approve → member list reflects change |
| P0 | `SchoolIsolationTest` | school A admin cannot see school B pending requests |
| P1 | `AuthServiceTest` | `/api/auth/me` includes schoolMemberships |
| P1 | `SchoolControllerTest` | `/api/schools` returns only active schools |
| P1 | `PlatformAdminControllerTest` | platform owner can create school |
| P2 | `ClubServiceTest` | findBySchoolAndClubSlug resolves correctly |
| P2 | `SchoolUserServiceTest` | add/remove school admin role |

### 10.2 Frontend tests (Vitest + Vue Test Utils)

| Priority | Test | What it verifies |
|----------|------|-----------------|
| P0 | `schoolStore.spec.ts` | setCurrentSchool, fetchSchoolBySlug |
| P0 | `schoolService.spec.ts` | API calls include correct school slug |
| P1 | `SchoolPickerView.spec.ts` | renders school cards from mock data |
| P1 | `SchoolSelector.spec.ts` | dropdown shows schools; selecting one changes currentSchool |
| P2 | `ClubDetailView.spec.ts` | fetches club with school slug context |

---

## 11. Risk Management

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Data migration breaks existing MVHS data | High | Run migration on copy first; keep old columns until Phase 4 |
| School‑scoped API change breaks frontend | High | Keep old endpoints live during Phase 2–3; dual‑run |
| Performance degradation with multiple schools | Medium | Add DB indexes; paginate club lists; cache school branding |
| Permission regression (cross‑school data leak) | High | School‑isolation tests mandatory before Phase 4 launch |
| OAuth redirect broken by new URL structure | Medium | Keep `/auth/callback` at root; redirect to school home after login |

---

## 12. Definition of Done (per phase)

1. All tasks in the phase are complete.
2. Backend tests pass: `cd backend && ./mvnw test`.
3. Frontend tests pass: `cd frontend && npm run test:unit`.
4. Frontend type‑check passes: `cd frontend && npm run type-check`.
5. Lint passes: `cd frontend && npm run lint`.
6. Manual smoke test: sign in → pick school → browse clubs → view detail → apply → (as admin) approve.
7. No regression in existing MVHS flows during Phases 1–3 (old endpoints still work).

---

## 13. Quick Reference: Running the Project

```bash
# Backend
cd backend
./mvnw spring-boot:run          # Starts on http://localhost:8080

# Frontend
cd frontend
npm install
npm run dev                     # Starts on http://localhost:4173

# Tests
cd backend && ./mvnw test
cd frontend && npm run test:unit
```

---

*Last updated: 2025-07-17*
*Next review: after Phase 1 completion*
