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
**Rule:** Keep the repo focused on a single-school site. Other schools should copy the pattern instead of sharing a multi-tenant platform.
**Rule:** All user-facing and documentation copy must remain in English only.
**Rule:** After completing a PR (merging/resolving conflicts), open a follow-up commit to self-review the changes and verify PR status checks pass before marking the PR as ready.

---

## Work Order (re-sorted by verified status)

Work through issues in this order. Completed issues are collapsed at the bottom.

| Seq | # | Issue | Status | Priority | Effort |
|-----|---|-------|--------|----------|--------|
| 1 | #9 | Verify president permissions | open | P0 | Small |
| 2 | #7 | Club creation UI in admin page | open | P0 | Medium |
| 3 | #5 | Account creation data collection (home school) | open | P1 | Medium |
| 4 | #8 | President assignment page | open | P1 | Medium |
| 5 | #14 | Profile page completeness | open | P1 | Small |
| 6 | #6 | Homepage images from DB | open | P1 | Small |
| 7 | #3 | Club summary API | open | P1 | Medium |
| 8 | #15 | Theme matching design PDF | open | P2 | Medium |
| 9 | #11 | Club recommendation page | open | P2 | Medium |
| 10 | #10 | Comment & rating system | deferred | P3 | Large |
| 11 | #12 | QR code club application | deferred | P3 | Medium |
| 12 | #13 | Meeting attendance system | deferred | P3 | Large |
| 13 | #20 | Define `clubs.visibility` semantics | open | P3 | Medium |
| 14 | #21 | Rate limiting on media publishing | open | P3 | Small |

---

## Issue #1: Single-School Direction Confirmed

**Status:** `done`
**PR:** #53
**Verified:** 2025-07-17 — Codebase is already single-school; direction confirmed. No action needed.

**Problem:**
The task list said "discard all the school changes, roll back the school structure changes." The codebase is already single-school (no school-scoped controllers, no `school_id` FK in clubs). The long-term direction is now confirmed as single-school. The task list and the roadmap no longer contradict.

**Solution:**
Decision made: the 1st repo stays single-school. Other schools will adopt the pattern by copying the repo rather than running a shared multi-tenant instance. Open issues that still reference school-scoped endpoints (`/api/schools/{slug}/...`) must be updated to the single-school `/api/clubs` pattern before implementation.

**Expected Outcome:**
- Single-school direction documented in CODET.md and roadmap.
- No dead code or dual-route confusion.

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
   - `GET /api/clubs` and `GET /api/clubs/{id}` (public read)
   - `GET /api/auth/providers` (public)
   - Everything else under `/api/**` -> `authenticated()`
2. Audit every controller method to confirm it has proper role checks (platform owner, school admin, club president).
3. Add a security checklist comment at the top of `SecurityConfig.java` documenting the authorization model.

**Expected Outcome:**
- No unauthenticated access to mutation endpoints (POST/PUT/DELETE).
- Security is enforced at the filter level as a safety net, not solely in controller methods.
- Clear documentation of which routes are public vs. authenticated.

---

## Issue #3: Add Club Summary Data API with Hash

**Status:** `open` (partial — generic `/api/summary` exists but is not school-scoped)
**PR:** —
**Verified:** 2025-07-17 — SummaryController returns all-club aggregate with hardcoded school name "HS Clubs". No school filtering. For single-school MVP this works; needs scoping before 2nd repo.

**Problem:**
The 2nd repo (multi-school aggregator) needs a public API from each 1st-repo instance that provides summary data: school identity, club count, category breakdowns, last-updated timestamp, and a hash/checksum to detect changes. This endpoint does not exist yet.

Relevant files:
- `backend/src/main/java/com/example/demo/club/controller/ClubController.java` — currently serves club data
- `backend/src/main/java/com/example/demo/club/service/ClubService.java`

**Solution:**
1. Add a new endpoint: `GET /api/clubs/summary`
2. The response should include:
```json
{
  "siteName": "Mountain View High School Clubs",
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
4. Add a `ClubSummary` model class and a `ClubSummaryService` or extend `ClubService`.

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
   - Home school (dropdown from `/api/clubs/schools` or pre-configured list)
   - Graduation year (dropdown, current year + 3)
   - Optional: interest categories (checkboxes matching club categories)
4. Submit to `POST /api/auth/onboarding` which populates `user_profiles` and optionally creates a `school_users` row.
5. After completion, redirect to home page.

**Expected Outcome:**
- New users have complete profiles from day one.
- Enables future personalization (recommendations) without requiring users to manually fill in profile later.

---

## Issue #6: Fix Homepage Images to Use Database Instead of Hardcoded Paths

**Status:** `completed`
**PR:** Implemented on the Instagram avatar fallback branch.

**Resolution:**
The homepage now randomly selects clubs that have an Instagram profile, displays their cached avatars, and links each hero image to the corresponding club detail page. The three hardcoded public images have been removed.

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
   - Add a user as president (new endpoint: `POST /api/clubs/{id}/presidents`).
   - Remove a president (new endpoint: `DELETE /api/clubs/{id}/presidents/{userId}`).
   - List current presidents for the club (extend `GET /api/clubs/{id}/members`).
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
   - `GET /api/clubs/{id}/reviews` (paginated, newest first)
   - `POST /api/clubs/{id}/reviews` (authenticated, one per user per club)
   - `PUT /api/clubs/{id}/reviews` (update own review)
   - `DELETE /api/clubs/{id}/reviews` (delete own review)
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
3. Backend: Add `GET /api/clubs/recommendations` that:
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
1. Each club gets a unique application URL: `/clubs/{id}/apply?src=qr`
2. Generate a QR code image for that URL (use a library like `qrcode` on the frontend or generate server-side).
3. Display the QR code on the club detail page and in the club admin page (for presidents to print/share).
4. When scanned, the URL opens the club detail page with the "Apply" button prominently displayed.
5. The apply flow reuses the existing `POST /api/clubs/{id}/members/apply` endpoint.

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
   - `POST /api/clubs/{id}/meetings` (president/admin creates a meeting)
   - `GET /api/clubs/{id}/meetings` (list upcoming meetings)
   - `POST /api/clubs/{id}/meetings/{meetingId}/attend` (member requests attendance)
   - `GET /api/clubs/{id}/meetings/{meetingId}/attendance` (president views requests)
   - `PUT /api/clubs/{id}/meetings/{meetingId}/attendance/{attendanceId}` (president confirms/rejects)
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

**Status:** `done`
**PR:** bangxiao0927/HSclubs#PENDING16
**Verified:** 2026-08-20 — Resolved by step 1 of the solution below, not by a token store. The
session already outlives a browser restart for a week:
`backend/src/main/resources/application.yaml` sets `server.servlet.session.timeout: 7d`
*and* `server.servlet.session.cookie.max-age: 7d`. Both are required and neither alone is
enough — the timeout governs how long the server keeps the session alive between requests,
while the cookie max-age is what stops `JSESSIONID` from being a session cookie the browser
discards on exit. The same block pins `same-site: lax` (a `strict` cookie is withheld on the
Google callback navigation and login fails with `authorization_request_not_found`) and
`secure`, which defaults to `false` for plain-HTTP local dev and **must** be set to `true` in
production via `SESSION_COOKIE_SECURE` — a 7-day cookie is far too long-lived to be allowed
onto a plain-HTTP request.

Spring Security's persistent `RememberMe` (steps 2-4 below) is deliberately **not**
implemented: it would add a `persistent_logins` table, a second credential to steal, and its
own expiry/cleanup job, all to extend a login period that a single config value already
covers for a school site where a week is the right answer. Revisit only if the desired
remember period grows past what a server-side session can reasonably hold.

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

**Status:** `done`
**PR:** bangxiao0927/HSclubs#45, bangxiao0927/HSclubs#85
**Verified:** 2026-08-04 — `WebConfig.java` still serves `/uploads/**` correctly (it later
gained an explicit `X-Content-Type-Options: nosniff` header via bangxiao0927/HSclubs#85, but
the resource mapping itself is unchanged since the 2025-07-17 pass). Both original gaps are
closed: `ClubImageController#uploadImage` deletes the
previous `imageUrl` file after the new one is committed (bangxiao0927/HSclubs#45), and
`UploadCleanupService` runs a nightly (3 AM) scheduled sweep, introduced in
bangxiao0927/HSclubs#45. That original sweep had neither a grace period nor any notion of
`club-posts/` (club posts did not exist yet); bangxiao0927/HSclubs#85 later added the
10-minute grace period, widened the scope to also walk `club-posts/` recursively, and
explicitly excluded the Instagram avatar cache. No open gap remains.

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
   -- Done in bangxiao0927/HSclubs#45.
4. Add a scheduled task to clean up orphaned upload files not referenced by any club.
   -- Done in bangxiao0927/HSclubs#45; rescoped to also walk `club-posts/` (and stay isolated
   from the Instagram avatar cache) in bangxiao0927/HSclubs#85.

**Expected Outcome:**
- Club images upload and display correctly.
- Old images are deleted when replaced.
- No disk space leaks from orphaned files under the legacy top-level upload location or
  `club-posts/`.

---

## Issue #18: Document SecurityConfig Authorization Model

**Status:** `done`
**PR:** bangxiao0927/HSclubs#PENDING18
**Verified:** 2026-08-20 — `SecurityConfig.java` now carries a class-level Javadoc covering the
whole model, and the stale summary block that had been left dangling *after* the closing brace
(so it documented nothing and had already drifted: no `/api/v1/summary`, no media feed, no
mobile-auth) is gone. What the new block states:

- **The dual layer.** The filter chain answers "who are you" and is a coarse safety net — every
  `/api/**` route is authenticated unless explicitly listed, so a new controller is private by
  default. Controllers answer "what may you do" (`requireManageAccess`, `requirePlatformOwner`),
  because that depends on the club addressed and the caller's membership row, which no path
  pattern can express. A bare `.authenticated()` must not be read as "any signed-in student may".
- **Every public route with its reason:** the CORS preflight, the login handshake
  (`/api/auth/**`, `/oauth2/**`, `/login/**`, `/error`), browsable club data
  (`GET /api/clubs`, `/api/clubs/calendar`, `/api/clubs/{id}`, `/api/clubs/recommendations`),
  the media feed and its comments (#78, #79), cached Instagram avatars, the aggregate the
  guiding page polls (`/api/summary`, `/api/v1/summary`), and everything outside `/api`.
- **The two mobile-auth endpoints.** `GET /api/mobile-auth/start` runs before a session exists
  and `POST /api/mobile-auth/complete` is what creates one, so neither *can* require
  authentication; their safety comes from the allow-listed Universal Link, the single-use code
  and the PKCE verifier, not from a session.
- **The two OAuth2 outcome handlers** added with mobile-auth: `handleLoginSuccess` and
  `handleLoginFailure` each branch on whether a mobile-auth flow is pending in the session — web
  sign-in redirects to the SPA exactly as before, an app sign-in is returned to the registered
  Universal Link with a one-time `code` or an `error`. No token or session identifier ever
  travels in a URL.
- **Session lifetime** (7-day timeout + 7-day cookie, see #16) and POST-only logout.
- **CSRF.** Off by default because the SPA does not echo the token back yet; opt-in via
  `app.security.csrf.enabled`. The detailed rationale (including why `/api/mobile-auth/complete`
  is in the ignore list and logout deliberately is not) already lived on `configureCsrf`, so the
  class comment points there rather than duplicating it, as it does for the two-policy CORS note
  on `corsConfigurationSource`.

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

## Issue #19: Club Media - Photo Feed, Comments, and Pinning

**Status:** `done`
**PR:** bangxiao0927/HSclubs#84, bangxiao0927/HSclubs#85, bangxiao0927/HSclubs#86, bangxiao0927/HSclubs#87, bangxiao0927/HSclubs#88, bangxiao0927/HSclubs#89, bangxiao0927/HSclubs#91
**Verified:** 2026-08-04 — All eight `/api/clubs/{clubSlugOrId}/posts...` endpoints exist and match `docs/API.md`; see that file for the full request/response reference.

**Problem:**
`docs/FIRST_REPO_ROADMAP.md` item 23 ("Media area and media timeline") was filed under
"P4 - Defer" because a social feed or story-like timeline creates moderation, storage, abuse,
and deletion requirements that did not have answers yet.

**Solution:**
Shipped a per-club photo post feed, implemented in issues bangxiao0927/HSclubs#76 through
bangxiao0927/HSclubs#82 (bangxiao0927/HSclubs#83, this entry's own issue, is the documentation
and roadmap reconciliation that followed, not implementation), with those requirements
answered directly:
1. `ClubPost`/`ClubPostComment` schema, mapper, and `ImageStorageService` hardening
   (bangxiao0927/HSclubs#76, bangxiao0927/HSclubs#77; PRs bangxiao0927/HSclubs#84,
   bangxiao0927/HSclubs#85).
2. Publish-a-post and read-the-feed endpoints (bangxiao0927/HSclubs#78; PR
   bangxiao0927/HSclubs#86).
3. Comments (capped at 50 per post) and moderation-gated deletion of posts and comments
   (bangxiao0927/HSclubs#79; PR bangxiao0927/HSclubs#88).
4. Pinning, capped at 3 posts per club (bangxiao0927/HSclubs#80; PR bangxiao0927/HSclubs#87).
5. The public, read-only media page (bangxiao0927/HSclubs#81; PR bangxiao0927/HSclubs#89).
6. Authenticated publish/comment/delete/pin interactions on that page
   (bangxiao0927/HSclubs#82; PR bangxiao0927/HSclubs#91).

**Expected Outcome:**
- Moderation: a club's own president, or a platform owner, can delete any post or comment in
  that club; only members can publish a post or a comment.
- Storage: every non-GIF upload is re-encoded to a flattened, EXIF-stripped JPEG capped at
  1600px; GIFs are capped at 2MB; a nightly (3 AM) job reclaims orphaned files under the
  legacy top-level club-image upload location and `club-posts/` after a 10-minute grace
  period, excluding the Instagram avatar cache and any other subsystem on the same upload
  root.
- Deletion: deleting a post removes its photo file from disk, not just its row.
- Abuse: partially addressed by per-file size caps and the 50-comment-per-post cap. Rate
  limiting on how often a member can publish is still open — see Issue #21 below.
- Left explicitly out of scope: `clubs.visibility` semantics (see Issue #20 below) and photo
  URLs (post images only, not author avatars) remain unauthenticated, unguessable capability
  URLs (see `docs/API.md`).

---

## Issue #20: `clubs.visibility` Column Has No Defined Semantics

**Status:** `open`
**PR:** —

**Problem:**
`clubs.visibility` (`schema.sql`, default `'public'`) is selected, inserted, and updated by
`ClubMapper`/`ClubMapper.xml`, but no query anywhere filters or branches on it. The only
visibility gating that exists is `ClubVisibilityPolicy`, which uses `clubs.status = 'active'`
for the club media feed (introduced in bangxiao0927/HSclubs#78; see
`docs/FIRST_REPO_ROADMAP.md` item 23 and this gap's own reconciliation in
bangxiao0927/HSclubs#83), not `clubs.visibility`. The club media feature deliberately did not
become the first place to enforce this column: hiding a club's post feed while
`GET /api/clubs/{id}` still returns that same club's full details publicly would be
incoherent, not a fix.

**Solution:**
Define private-club behavior as its own standalone design task before writing any code that
reads `clubs.visibility`:
1. Decide what "private" should mean for a club: hidden from `GET /api/clubs` listing only,
   hidden from `GET /api/clubs/{id}` detail too, or hidden from the media feed and comments
   as well.
2. Decide who can still see a private club (members, the president, platform owners; anyone
   else at all).
3. Apply that decision consistently across every endpoint that currently treats "active" as
   "publicly visible" (`ClubController`, `ClubVisibilityPolicy`, the summary/recommendation
   endpoints), not just the media feed.
4. Only then start reading `clubs.visibility` in a `WHERE` clause or policy check.

**Expected Outcome:**
- `clubs.visibility` either has a documented, enforced meaning everywhere club visibility is
  decided, or is removed if it turns out to duplicate `clubs.status`.
- No endpoint is left checking `clubs.status` for the general case and `clubs.visibility` for
  another, with the two silently able to disagree.

---

## Issue #21: No Rate Limiting on Media Publishing

**Status:** `open`
**PR:** —

**Problem:**
Neither `ClubPostController#publish` nor `ClubPostCommentController#create` limits how often
a single member can call them. The only constraints in place are per-request: a 5MB
(JPEG/PNG/WebP) or 2MB (GIF) file size cap per post, a 140-character title cap, a
300-character comment body cap, and a 50-comment-per-post cap. A member who is otherwise in
good standing can loop the publish endpoint to fill disk space with posts, or loop the
comment endpoint to fill a post's 50-comment cap on many posts in quick succession. No
rate-limiting mechanism (in-memory token bucket, Redis-backed limiter, or similar) exists
anywhere in the codebase yet.

**Solution:**
1. Pick a scope and window: per-member-per-club, or per-member platform-wide; per-minute,
   per-hour, or per-day.
2. Pick a mechanism appropriate for a single-instance deployment first (e.g. an in-memory
   token bucket keyed by `oauth_user_id`), since this is a single-school app with one backend
   instance; only move to a shared store (Redis, a database-backed counter) if the app is
   ever run with more than one backend instance.
3. Return 429 (or the existing 409 "try again" convention already used for the comment-cap
   and pin-cap conflicts) with a message the frontend can show as a cooldown notice.
4. Add the limiter to both `ClubPostController#publish` and
   `ClubPostCommentController#create`; consider whether pin/unpin need it too (lower priority:
   they do not create new storage).

**Expected Outcome:**
- A member cannot exhaust disk space or spam a club's feed by looping the publish or comment
  endpoint.
- The limiter fails closed (rejects) rather than failing open if its own state is
  unavailable.

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
| 17 | Club image storage verify & cleanup | done | — | — |
| 18 | Document SecurityConfig | open | P0 | Tiny |
| 19 | Club media - photo feed, comments, pinning | done | — | — |
| 20 | Define `clubs.visibility` semantics | open | P3 | Medium |
| 21 | Rate limiting on media publishing | open | P3 | Small |
