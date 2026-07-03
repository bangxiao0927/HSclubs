# Execution Criteria

This document turns the 1st repo roadmap into development-ready work. Use it when creating issues, reviewing pull requests, or deciding whether a task is done.

## Definition of Done

Every implementation issue should include:

1. Acceptance criteria.
2. A small issue scope.
3. A verification checklist.
4. Updated docs when the behavior, setup, API, or development order changes.

An issue is not done just because the code compiles. It is done when the expected user behavior works, the risky edge cases are checked, and the verification steps are recorded.

## Acceptance Criteria

### P0.1 Security and permissions

Done means:

- Student requests to admin-only club create, update, delete, roster, and pending-request APIs return `403`.
- Non-president users cannot manage clubs they are not assigned to.
- Club presidents can only manage their own club.
- School admins can manage clubs only inside their school scope unless they are also platform owners.
- Platform owners can still use global owner workflows.
- Frontend admin links are hidden for users who cannot manage the resource.
- Backend authorization does not rely only on hidden frontend links.
- Permission tests cover student, club president, school admin, and platform owner cases.

### P0.2 Local database and dev setup

Done means:

- A new maintainer can start backend and frontend from documented commands.
- Unsafe default passwords are removed from committed configuration.
- Required env vars are documented.
- Test database setup remains separate from normal development database setup.
- Backend tests run without requiring a remote database.
- README or deployment docs explain the local database path clearly.

### P0.3 API plan review

Done means:

- School-scoped APIs are documented as the primary path.
- Legacy APIs are clearly marked as compatibility routes.
- New work does not introduce duplicate endpoint shapes for the same workflow.
- Future summary API needs are listed before implementation begins.
- API docs match the implemented request and response shapes.

### P0.4 Mobile UI pass

Done means:

- At iPhone-width viewport, homepage, school page, club search, club detail, profile, club admin, and pending requests do not horizontally overflow.
- Main actions remain visible and tappable.
- Text does not overlap cards, buttons, images, or navigation.
- Tables or dense admin rows collapse into a mobile-friendly layout.
- Forms can be completed on a phone without hidden fields or clipped buttons.
- A manual mobile viewport smoke check is recorded.

### P0.5 Club search

Done means:

- Search matches club name.
- Search matches alias.
- Search matches category.
- Search matches advisor or contact when available.
- Search handles case differences and extra spaces.
- Empty results show a helpful state.
- Search works in the school-scoped route.
- Unit tests or focused frontend tests cover the matching utility.

### P0.6 User center/profile

Done means:

- Profile shows account identity.
- Profile shows home school when available.
- Profile shows graduation year when available.
- Profile shows memberships or application status when available.
- Profile links users back to their school and relevant clubs.
- Empty or missing data states are clear.
- Logged-out users are handled gracefully.

### P1.1 Account creation data collection

Done means:

- New users can provide home school.
- New users can provide graduation year.
- Interest categories can be collected if recommendation work needs them.
- Required fields are validated.
- Users can correct mistakes later from profile/settings.

### P1.2 User agreements

Done means:

- Terms, privacy notes, and community expectations are accessible.
- Account setup can record agreement if the product requires it.
- Copy is short enough for students and school admins to understand.
- No legal-heavy flow blocks basic browsing unless required.

### P1.3 President edit flow

Done means:

- President can edit assigned club details.
- President can review that club's membership requests.
- President can refresh member/request data without leaving the page.
- President cannot reach or submit changes for unrelated clubs.
- Permission failures show a clear frontend message.

### P1.4 Admin club creation

Done means:

- School admin can create a club from the school admin area.
- Required fields are clear.
- Validation errors are shown inline.
- Created club appears in school search and club listings.
- A non-admin cannot create clubs through the frontend or backend.

### P1.5 President assignment

Done means:

- School admin can assign a user as president for a club.
- Assignment changes backend permissions immediately.
- The assigned president sees the management entry point after refresh/session reload.
- The previous state is handled clearly if there was already a president.

### P1.6 Local club image storage

Done means:

- Uploaded images are stored locally in the configured upload directory.
- Backend validates file type.
- Backend rejects oversized files.
- Stored file names cannot escape the upload directory.
- Frontend shows upload progress or disabled state.
- Frontend shows a clear error on failed upload.

### P1.7 Homepage images and theme

Done means:

- Homepage uses school or club image data when available.
- Fallback images are stable and not broken.
- Theme values live in shared CSS variables or an equivalent shared styling layer.
- Theme changes do not break mobile layout or admin readability.

### P2 Summary API preparation

Done means:

- Public summary endpoint exposes only non-private data.
- Response includes school identity, status, club count, category counts, and update time.
- Hash/checksum is included only if it has a clear consumer.
- API docs include the endpoint.
- Tests verify that private user/admin data is not returned.

## Issue Breakdown

Use these as the first issue set. Each issue should be small enough to finish and verify independently.

### Security and permissions

- Audit backend permission helpers.
- Add student forbidden tests for admin endpoints.
- Add non-president forbidden tests for club management.
- Add president allowed tests for assigned club management.
- Add school admin school-scope tests.
- Hide unauthorized frontend admin links.
- Add frontend permission failure states.

### Local dev and configuration

- Remove unsafe default secrets/passwords from config.
- Add `.env` documentation for backend.
- Document local MySQL setup.
- Confirm H2-backed tests do not need MySQL.
- Fix duplicated or confusing config blocks.

### Mobile UI

- Homepage mobile layout.
- School page mobile layout.
- Club search mobile layout.
- Club detail mobile layout.
- Profile mobile layout.
- Club admin mobile layout.
- Pending requests mobile layout.
- Navigation/header mobile behavior.

### Club search

- Centralize club search matching utility.
- Add name and alias matching.
- Add category matching.
- Add advisor/contact matching.
- Add empty-state UI.
- Add tests for case-insensitive and whitespace-tolerant search.

### Profile and account setup

- Profile identity section.
- Home school display/edit path.
- Graduation year display/edit path.
- Membership/application status section.
- Account setup form for missing data.
- User agreement pages or setup step.

### President workflow

- Verify president role source of truth.
- Club edit permission hardening.
- Pending requests management polish.
- Members list visibility and refresh.
- Unauthorized club admin route handling.

### School admin workflow

- Club creation form.
- Club creation API wiring and validation.
- President assignment UI.
- President assignment API wiring.
- School admin dashboard navigation separation.
- School-level stats or warnings.

### Images and theme

- Upload validation backend.
- Upload error states frontend.
- Local image URL handling.
- Homepage image source cleanup.
- Shared theme variables.
- PDF default theme application.

### Future summary API

- Define summary response shape.
- Implement summary endpoint.
- Add no-private-data tests.
- Document summary endpoint.
- Add sample response for future 2nd repo.

## Verification Checklist

### Per issue

- Acceptance criteria are checked.
- Relevant backend tests pass.
- Relevant frontend tests or type checks pass.
- Manual smoke test is recorded when UI changes.
- Mobile viewport check is recorded when layout changes.
- API docs are updated when endpoint behavior changes.

### Phase 1 - Safety and local reliability

- Run backend tests.
- Confirm student admin API access returns `403`.
- Confirm non-president club management returns `403`.
- Confirm assigned president can manage their club.
- Confirm school admin scope is enforced.
- Start backend from documented local setup.
- Start frontend from documented local setup.

### Phase 2 - Student-facing core

- Run frontend type-check.
- Run club search tests.
- Manually test homepage at mobile width.
- Manually test club search at mobile width.
- Manually test club detail at mobile width.
- Manually test profile logged in, logged out, and missing-data states.

### Phase 3 - Club president workflow

- Run backend permission tests.
- Manually test assigned president edit flow.
- Manually test assigned president pending request flow.
- Manually test unrelated president forbidden flow.
- Manually test club admin page at mobile width.

### Phase 4 - School admin workflow

- Run backend admin tests.
- Manually create a club as school admin.
- Confirm created club appears in school listing and search.
- Assign a president and confirm the president can manage the club.
- Confirm a student cannot create clubs or assign presidents.

### Phase 5 - Visual polish and theme

- Run frontend type-check.
- Run production build.
- Check homepage, search, detail, profile, and admin pages at mobile width.
- Check the same pages at desktop width.
- Confirm text, buttons, forms, and navigation do not overlap.
