# HSclubs 1st Repo - Issues & Action Items

Each issue includes: Status, PR, Problem, Solution, Expected Outcome.

## Status Legend

| Status | Meaning |
|--------|---------|
| `open` | Not started — ready to be picked up |
| `in-progress` | Someone is actively working on it |
| `done` | Completed and verified against codebase |
| `invalid` | No longer applicable (e.g. code diverged, issue moot) |
| `deferred` | Intentionally postponed per roadmap priority |

**Rule:** When an issue is completed, fill in `Status: done`, the PR number, and the date. Never leave a completed issue unmarked — future sessions depend on this.

---

## Work Order (re-sorted by verified status)

Work through issues in this order. Completed issues are collapsed at the bottom.

| Seq | # | Issue | Status | Priority | Effort |
|-----|---|-------|--------|----------|--------|
| 1 | #9 | Verify president permissions | open | P0 | Small |
| 2 | #17 | Club image storage verify & cleanup | open | P0 | Small |
| 3 | #7 | Club creation UI in admin page | open | P0 | Medium |
| 4 | #18 | Document SecurityConfig authorization model | open | P0 | Tiny |
| 5 | #5 | Account creation data collection (home school) | open | P1 | Medium |
| 6 | #8 | President assignment page | open | P1 | Medium |
| 7 | #14 | Profile page completeness | open | P1 | Small |
| 8 | #6 | Homepage images from DB | open | P1 | Small |
| 9 | #3 | School-scoped summary API | open | P1 | Medium |
| 10 | #16 | Login remember period | open | P2 | Small |
| 11 | #15 | Theme matching design PDF | open | P2 | Medium |
| 12 | #11 | Club recommendation page | open | P2 | Medium |
| 13 | #10 | Comment & rating system | deferred | P3 | Large |
| 14 | #12 | QR code club application | deferred | P3 | Medium |
| 15 | #13 | Meeting attendance system | deferred | P3 | Large |

---

## Issue #1: Resolve Multi-School Architecture Contradiction

**Status:** `invalid`
**PR:** —
**Verified:** 2025-07-17 — No school-scoped controllers exist in the codebase, no `school_id` FK in clubs table. Codebase is already single-school. Issue description was based on outdated plan; no action needed.

**Problem:**
The task list says "discard all the school changes, roll back the school structure changes." However, the codebase already has a full multi-school architecture built out: `schools` database table, school-scoped API routes (`/api/schools/{slug}/clubs/...`), school-scoped frontend routes (`/schools/:schoolSlug/...`), and the `FIRST_REPO_ROADMAP.md` explicitly states the long-term direction is multi-school. The task list and the roadmap contradict each other.

**Solution:**
1. Confirm with stakeholders whether the 1st repo should remain single-school (MVHS only) or keep the multi-school structure.
2. If single-school: Remove the `schools` table foreign keys from clubs, remove school-scoped routes, simplify `SchoolClubController` into `ClubController`, remove school-picker and multi-school frontend routes.
3. If multi-school stays: Close this issue as "won't fix" and update the task list to reflect the decision.

**Expected Outcome:**
- Clear, documented decision on school scoping.
- No dead code or confusing dual-route architecture.
- All routes follow a single consistent pattern.

---

## Issue #2: Tighten API Security - Blanket permitAll()

**Status:** `done`
**PR:** (pre-existing — no PR recorded)
**Verified:** 2025-07-17 — SecurityConfig.java already has `.requestMatchers("/api/**").authenticated()` with explicit public exceptions for `/api/auth/**`, `GET /api/clubs`, `GET /api/clubs/calendar`, `GET /api/clubs/*`, `GET /api/summary`. CSRF disabled with OAuth SPA justification. Issue doc was outdated.

**Problem:**
`SecurityConfig.java` line 49 has:
```java
.requestMatchers("/api/**").permitAll()
```
This means every `/api/**` endpoint is open at the Spring Security filter level. Individual controller methods do their own authentication checks (e.g., `requireManageAccess`, `requirePlatformOwner`), but a single missed check on any new or existing endpoint would expose it without authentication. This is fragile and error-prone.

CSRF protection is disabled globally (line 41: `csrf.disable()`), which is acceptable for an OAuth-based SPA but should be documented.

**Solution:**
1. Change `SecurityConfig.java` to deny `/api/**` by default and explicitly permit only the public endpoints:
   - `GET /api/schools` and `GET /api/schools/{slug}` (public)
   - `GET /api/schools/{slug}/clubs` and `GET /api/schools/{slug}/clubs/{id}` (public read)
   - `GET /api/auth/providers` (public)
   - Everything else under `/api/**` -> `authenticated()`
2. Audit every controller method to confirm it has proper role checks (platform owner, school admin, club president).
3. Add a security checklist comment at the top of `SecurityConfig.java` documenting the authorization model.

**Expected Outcome:**
- No unauthenticated access to mutation endpoints (POST/PUT/DELETE).
- Security is enforced at the filter level as a safety net, not solely in controller methods.
- Clear documentation of which routes are public vs. authenticated.

---

## Issue #3: Add School Summary Data API with Hash

**Status:** `open` (partial — generic `/api/summary` exists but is not school-scoped)
**PR:** —
**Verified:** 2025-07-17 — SummaryController returns all-club aggregate with hardcoded school name "HS Clubs". No school filtering. For single-school MVP this works; needs scoping before 2nd repo.

**Problem:**
The 2nd repo (multi-school aggregator) needs a public API from each 1st-repo instance that provides summary data: school identity, club count, category breakdowns, last-updated timestamp, and a hash/checksum to detect changes. This endpoint does not exist yet.

Relevant files:
- `backend/src/main/java/com/example/demo/school/controller/SchoolController.java` — currently only lists schools and gets by slug
- `backend/src/main/java/com/example/demo/school/service/SchoolService.java`

**Solution:**
1. Add a new endpoint: `GET /api/schools/{slug}/summary`
2. The response should include:
```json
{
  "schoolName": "Mountain View High School",
  "shortName": "MVHS",
  "slug": "mvhs",
  "status": "active",
  "clubCount": 42,
  "categories": { "Academic": 15, "Sports": 10, "Arts": 8, "Service": 9 },
  "memberCount": 350,
  "lastUpdatedAt": "2025-07-01T12:00:00Z",
  "dataHash": "sha256-hex-digest"
}
```
3. The `dataHash` should be computed from club names, categories, and member counts so the 2nd repo can detect changes without re-fetching all data.
4. Add a `SchoolSummary` model class and a `SchoolSummaryService` or extend `SchoolService`.

**Expected Outcome:**
- 2nd repo can poll this endpoint to stay in sync.
- Data changes are detectable via hash comparison, minimizing bandwidth.

---

## Issue #4: Add User Agreements (Terms of Use & Privacy Policy)

**Status:** `done`
**PR:** (pre-existing — no PR recorded)
**Verified:** 2025-07-17 — TermsOfUseView.vue, PrivacyPolicyView.vue, AcceptTermsView.vue all exist and routed. Backend POST /api/auth/accept-terms works. oauth_users.accepted_terms_at column exists.

**Problem:**
The platform has no terms of use, privacy policy, or community guidelines pages. For a school-facing platform that handles student data via Google OAuth, this is a legal and trust requirement.

**Solution:**
1. Create a `TermsOfUseView.vue` at route `/terms`
2. Create a `PrivacyPolicyView.vue` at route `/privacy`
3. Add a simple consent step: After first OAuth login, if the user hasn't accepted terms yet, redirect to an `AcceptTermsView.vue` that shows a summary and a checkbox + "I agree" button.
4. Add an `accepted_terms_at TIMESTAMP` column to the `oauth_users` table.
5. Add a backend endpoint `POST /api/auth/accept-terms` that records acceptance.
6. Add footer links to Terms and Privacy on all pages.

**Expected Outcome:**
- Users must accept terms before using the platform.
- Terms and Privacy pages are accessible from the footer.
- Compliance with basic legal expectations for a school platform.

---

## Issue #5: Add Data Collection During Account Creation

**Status:** `open`
**PR:** —

**Problem:**
When a user signs in via Google OAuth for the first time, there is no onboarding step to collect essential profile data (home school, graduation year, interests). Currently, users are created silently in `CustomOAuth2UserService.recordLogin()`, and graduation year is only editable later in ProfileView.

Relevant files:
- `backend/src/main/java/com/example/demo/security/CustomOAuth2UserService.java`
- `backend/src/main/java/com/example/demo/auth/service/OAuthUserService.java`
- `frontend/src/views/AuthCallbackView.vue`

**Solution:**
1. After OAuth callback, check if the user has a `user_profiles` row or if `graduation_year IS NULL`.
2. If profile is incomplete, redirect to a new `OnboardingView.vue` instead of the home page.
3. The onboarding page collects:
   - Home school (dropdown from `/api/schools`)
   - Graduation year (dropdown, current year + 3)
   - Optional: interest categories (checkboxes matching club categories)
4. Submit to `POST /api/auth/onboarding` which populates `user_profiles` and optionally creates a `school_users` row.
5. After completion, redirect to home page.

**Expected Outcome:**
- New users have complete profiles from day one.
- Enables future personalization (recommendations) without requiring users to manually fill in profile later.

---

## Issue #6: Fix Homepage Images to Use Database Instead of Hardcoded Paths

**Status:** `open`
**PR:** —

**Problem:**
`HomeView.vue` line 92 hardcodes hero images:
```typescript
const heroImages = ['/hsclubs1.jpg', '/hsclubs2.png', '/hsclubs3.png']
```
This means every school deployment must manually provide these exact filenames in the public folder. Images should come from the database (e.g., school banner, club images) or be configurable.

**Solution:**
1. Use `currentSchool.bannerUrl` from the school store as the primary hero image.
2. Fall back to top clubs' `imageUrl` values for the carousel if no school banner exists.
3. As a last resort, keep one default placeholder image.
4. Update `SchoolClubController` (or a new endpoint) to return featured/hero data.
5. Remove the hardcoded array and replace with reactive data from the API.

**Expected Outcome:**
- Hero images are driven by database content (school banner, club images).
- No hardcoded file paths that break on new deployments.
- Each school can customize its own homepage visuals.

---

## Issue #7: Add Club Creation Form in Admin Page

**Status:** `open`
**PR:** —
**Verified:** 2025-07-17 — OwnerAdminView.vue lists clubs with filter/search but has no "Create Club" button or form. Backend POST /api/clubs exists (platform-owner only). Needs UI + school-admin role support.

**Problem:**
`OwnerAdminView.vue` lists existing clubs with search/filter but has no "Create Club" button or form. School admins and platform owners cannot create new clubs through the UI. They must use the API directly.

**Solution:**
1. Add a "Create Club" button in `OwnerAdminView.vue` header.
2. On click, show a modal or inline form with fields:
   - Club name (required)
   - Alias/short name
   - Description
   - Category (dropdown from `club_category` table)
   - Meeting schedule
   - Location
   - Contact email
   - Advisor name
   - Member count
3. On submit, call `POST /api/clubs`.
4. Refresh the club list on success.

**Expected Outcome:**
- School admins can create clubs without API tools.
- Form validates required fields before submission.

---

## Issue #8: Add President Assignment Page in Admin

**Status:** `open`
**PR:** —

**Problem:**
The `club_member` table has a `role_name` column that supports `president` role, and `ClubAdminView` checks `canManage` based on membership. However, there is no UI for a school admin or platform owner to assign a user as president of a club. Presidents must be inserted directly into the database.

**Solution:**
1. Add a "Manage Presidents" section to `ClubAdminView.vue` (visible to platform owners and school admins).
2. Include:
   - Search users by email or display name (new endpoint: `GET /api/users/search?q=...`).
   - Add a user as president (new endpoint: `POST /api/schools/{slug}/clubs/{id}/presidents`).
   - Remove a president (new endpoint: `DELETE /api/schools/{slug}/clubs/{id}/presidents/{userId}`).
   - List current presidents for the club (extend `GET /api/schools/{slug}/clubs/{id}/members`).
3. Add backend permission checks: only platform owner or school admin can assign presidents.

**Expected Outcome:**
- Admins can assign and revoke president roles through the UI.
- Presidents are correctly reflected in `club_member` with `role_name = 'president'`.

---

## Issue #9: Verify President Permissions on Backend Endpoints

**Status:** `open`
**PR:** —
**Verified:** 2025-07-17 — ClubService.canManage checks roleName === "president". ClubController has requireManageAccess and requirePlatformOwner helpers. Needs end-to-end audit of every mutation endpoint to confirm presidents can only manage their assigned club.

**Problem:**
The task requires verifying that club presidents can only manage their own clubs and cannot access or modify other clubs' data, rosters, or pending requests. Current code has `ClubService.canManage` that checks `roleName === 'president'`, but it's not clear if this is consistently applied across all endpoints.

**Solution:**
1. Audit `ClubController.java` — every mutation endpoint (`PUT`, `DELETE`, membership approvals, image upload) must call `requireManageAccess()`.
2. Audit `ClubImageController.java` — image upload must call `requireManageAccess()`.
3. Check that `ClubService.applyViewerPermissions()` correctly sets `canManage` and `canManageMembers`.
4. Verify `ClubAdminView` hides/shows sections based on `canManageMembers` computed property.
5. Check that the `ClubService` sets `canManage = true` only when the viewer is a president of that specific club OR a school admin OR a platform owner.
6. Fix any gaps found.

**Expected Outcome:**
- Presidents can manage only their own club(s).
- Non-presidents cannot access admin functions.
- School admins and platform owners retain override access.

---

## Issue #10: Add Comment & Rating System for Clubs

**Status:** `deferred`
**PR:** —

**Problem:**
No comment or rating feature exists. The task requires a system where logged-in users can leave comments and ratings on clubs.

**Solution:**
1. Create database table:
```sql
CREATE TABLE club_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    oauth_user_id BIGINT NOT NULL,
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_club_review (club_id, oauth_user_id),
    FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
);
```
2. Backend: Add `ClubReview` model, mapper, service, and controller endpoints:
   - `GET /api/schools/{slug}/clubs/{id}/reviews` (paginated, newest first)
   - `POST /api/schools/{slug}/clubs/{id}/reviews` (authenticated, one per user per club)
   - `PUT /api/schools/{slug}/clubs/{id}/reviews` (update own review)
   - `DELETE /api/schools/{slug}/clubs/{id}/reviews` (delete own review)
3. Frontend: Add review section to `ClubDetailView.vue` with star rating input and comment textarea.
4. Show average rating and review count on club cards.

**Expected Outcome:**
- Authenticated users can rate clubs 1-5 stars and leave comments.
- One review per user per club (enforced by unique constraint).
- Reviews display on club detail pages with average rating.

---

## Issue #11: Add Club Recommendation Page

**Status:** `open`
**PR:** —

**Problem:**
No recommendation feature exists. The task requires an interest-based club recommendation page for logged-in users.

**Solution:**
1. Add `interest_categories` column (JSON or comma-separated) to `user_profiles` table (populated during onboarding from Issue #5).
2. Create `RecommendationView.vue` at route `/recommendations`.
3. Backend: Add `GET /api/schools/{slug}/recommendations` that:
   - Reads the user's interest categories.
   - Finds clubs with matching categories that the user hasn't joined.
   - Sorts by category match count, then by member count or rating.
   - Returns top N results.
4. If the user has no interests set, fall back to popular clubs (highest member count).
5. Add a "Recommended for You" section to the home page for authenticated users.

**Expected Outcome:**
- Logged-in users see personalized club suggestions.
- Cold-start fallback for users without interest data.
- No complex ML — simple rule-based matching.

---

## Issue #12: Add QR Code Club Application

**Status:** `deferred`
**PR:** —

**Problem:**
No QR code feature exists. The task requires scanning a QR code to apply to a club.

**Solution:**
1. Each club gets a unique application URL: `/schools/{slug}/clubs/{id}/apply?src=qr`
2. Generate a QR code image for that URL (use a library like `qrcode` on the frontend or generate server-side).
3. Display the QR code on the club detail page and in the club admin page (for presidents to print/share).
4. When scanned, the URL opens the club detail page with the "Apply" button prominently displayed.
5. The apply flow reuses the existing `POST /api/schools/{slug}/clubs/{id}/members/apply` endpoint.

**Expected Outcome:**
- Each club has a scannable QR code.
- Scanning opens a mobile-friendly apply page.
- Presidents can download/print QR codes for events.

---

## Issue #13: Add Meeting Attendance System

**Status:** `deferred`
**PR:** —

**Problem:**
No attendance feature exists. The task requires: (a) members can request attendance at meetings, and (b) presidents can check/verify attendance.

**Solution:**
1. Create database tables:
```sql
CREATE TABLE club_meetings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    meeting_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    location VARCHAR(150),
    created_by_oauth_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by_oauth_user_id) REFERENCES oauth_users(uid)
);

CREATE TABLE meeting_attendance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    oauth_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL,
    confirmed_by_oauth_user_id BIGINT NULL,
    UNIQUE KEY uq_meeting_attendance (meeting_id, oauth_user_id),
    FOREIGN KEY (meeting_id) REFERENCES club_meetings(id) ON DELETE CASCADE,
    FOREIGN KEY (oauth_user_id) REFERENCES oauth_users(uid) ON DELETE CASCADE
);
```
2. Backend endpoints:
   - `POST /api/schools/{slug}/clubs/{id}/meetings` (president/admin creates a meeting)
   - `GET /api/schools/{slug}/clubs/{id}/meetings` (list upcoming meetings)
   - `POST /api/schools/{slug}/clubs/{id}/meetings/{meetingId}/attend` (member requests attendance)
   - `GET /api/schools/{slug}/clubs/{id}/meetings/{meetingId}/attendance` (president views requests)
   - `PUT /api/schools/{slug}/clubs/{id}/meetings/{meetingId}/attendance/{attendanceId}` (president confirms/rejects)
3. Frontend: Add "Meetings" tab to `ClubDetailView.vue` for members, and meeting management to `ClubAdminView.vue` for presidents.

**Expected Outcome:**
- Presidents can schedule meetings.
- Members can request attendance.
- Presidents can confirm or reject attendance requests.
- Attendance history is tracked per club.

---

## Issue #14: Fix User Center / Profile Page Completeness

**Status:** `open`
**PR:** —
**Verified:** 2025-07-17 — ProfileView.vue shows email, user ID, graduation year only. Missing: home school display, interests, member-since date, empty states for no clubs/applications.

**Problem:**
`ProfileView.vue` shows avatar, name, email, graduation year, "My Clubs," and "My Applications." However, it is missing:
- Home school display and editing
- Interest categories display and editing
- Link to school page
- Account creation date
- Consistent empty states when no clubs/applications exist

**Solution:**
1. Add "Home School" section showing the user's school name with a link to the school page.
2. If `home_school_id` is NULL (user hasn't set one), show a prompt to select a school.
3. Add "Interests" section (if recommendation system exists).
4. Add "Member since" date from `oauth_users.created_at`.
5. Ensure all empty states show helpful messages instead of blank sections.

**Expected Outcome:**
- Profile page is the complete user anchor.
- All profile data is viewable and editable in one place.
- Clear navigation to school and club pages.

---

## Issue #15: Apply Theme from Design PDF

**Status:** `open`
**PR:** —

**Problem:**
The task says "change the whole theme to the theme in the pdf as default." The current theme (`base.css`) uses blue tones with CSS custom properties. Without the PDF, we cannot verify if the current theme matches the design spec.

**Solution:**
1. Obtain the design PDF and extract: primary colors, secondary colors, font families, border radius values, spacing scale, and component-specific styles.
2. Compare against current `base.css` variables (`--mv-*` custom properties).
3. Update CSS custom properties to match the PDF exactly.
4. Update any hardcoded colors in individual components to use the CSS variables.
5. Ensure both light and dark modes are covered.

**Expected Outcome:**
- Visual design matches the approved PDF spec.
- All colors use CSS custom properties for maintainability.
- Light and dark themes both match the design system.

---

## Issue #16: Review Login Remember Period

**Status:** `open`
**PR:** —

**Problem:**
`SecurityConfig.java` uses `SessionCreationPolicy.IF_REQUIRED` with `JSESSIONID` cookies. The session lasts until the browser closes or the server-side session expires (default 30 min inactivity in Spring). There is no persistent "Remember Me" option. Users will be logged out frequently.

**Solution:**
1. Evaluate whether a longer session timeout is acceptable (e.g., 7 days via `server.servlet.session.timeout`).
2. If persistent login is needed, implement Spring Security's `RememberMe` with a persistent token store in the database:
```sql
CREATE TABLE persistent_logins (
    username VARCHAR(64) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);
```
3. Add a "Remember Me" checkbox on the login page.
4. Configure `rememberMe()` in `SecurityConfig.java`.

**Expected Outcome:**
- Users stay logged in across browser restarts.
- Remember-me tokens are stored securely (hashed) in the database.
- Token expiration and cleanup are handled.

---

## Issue #17: Verify Club Image Storage & Serving

**Status:** `open`
**PR:** —
**Verified:** 2025-07-17 — WebConfig.java serves /uploads/** correctly. Gaps: old image not deleted on replacement (orphan leak), no scheduled cleanup of unreferenced files.

**Problem (Verification):**
`ClubImageController.java` saves images to `backend/uploads/` with UUID filenames and returns `/uploads/{uuid}.{ext}` URLs. Need to verify:
- Images are served correctly via static resource mapping.
- The upload directory is writable in production.
- Old images are cleaned up when a new image is uploaded (or old one is orphaned).
- The frontend `clubImage()` utility correctly resolves image URLs.

**Solution:**
1. Confirm Spring Boot serves `/uploads/**` from the `uploads/` directory (check `WebConfig.java` or `application.yaml`).
2. Test upload flow end-to-end: select file -> upload -> verify image displays.
3. Delete old image file when a club's image is updated (currently the old file is orphaned).
4. Add a scheduled task to clean up orphaned upload files not referenced by any club.

**Expected Outcome:**
- Club images upload and display correctly.
- Old images are deleted when replaced.
- No disk space leaks from orphaned files.

---

## Issue #18: Document SecurityConfig Authorization Model

**Status:** `open`
**PR:** —

**Problem:**
`SecurityConfig.java` already blocks `/api/**` by default and whitelists public endpoints, but there is no documentation explaining:
- Which routes are public vs. authenticated and why.
- Why CSRF is disabled (acceptable for OAuth-based SPA, but should be noted).
- How controller-level role checks (requireManageAccess, requirePlatformOwner) complement filter-level auth.

A future maintainer opening `SecurityConfig.java` has to reverse-engineer the intent.

**Solution:**
1. Add a block comment at the top of `SecurityConfig.java` listing every public route and its rationale.
2. Add a one-line comment next to `csrf.disable()` explaining the OAuth SPA justification.
3. Document the dual-layer model: Spring Security handles authentication (who you are); controller methods handle authorization (what you can do).

**Expected Outcome:**
- Any maintainer can understand the security model in under 2 minutes by reading the config file.

---

## Summary Table (historical — see Work Order at top for current sequence)

| # | Issue | Status | Priority | Effort |
|---|-------|--------|----------|--------|
| 1 | Multi-school contradiction | invalid | — | — |
| 2 | Security hardening | done | — | — |
| 3 | Summary data API | open (partial) | P1 | Medium |
| 4 | User agreements | done | — | — |
| 5 | Account creation data collection | open | P1 | Medium |
| 6 | Homepage images from DB | open | P1 | Small |
| 7 | Club creation in admin | open | P0 | Medium |
| 8 | President assignment page | open | P1 | Medium |
| 9 | Verify president permissions | open | P0 | Small |
| 10 | Comment & rating system | deferred | P3 | Large |
| 11 | Club recommendation page | open | P2 | Medium |
| 12 | QR code application | deferred | P3 | Medium |
| 13 | Meeting attendance | deferred | P3 | Large |
| 14 | Profile page completeness | open | P1 | Small |
| 15 | Theme matching PDF | open | P2 | Medium |
| 16 | Login remember period | open | P2 | Small |
| 17 | Club image storage verify | open | P0 | Small |
| 18 | Document SecurityConfig | open | P0 | Tiny |
