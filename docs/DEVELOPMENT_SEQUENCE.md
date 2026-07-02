# Development Sequence

This sequence is the recommended build order for the 1st repo. It favors useful functionality, short cycles, and low maintenance.

## Guiding Rule

Do not build the aggregator or mobile repo before the sample school workflow is stable.

A feature is ready to move forward when it improves this loop:

```text
open school site
  -> search or browse clubs
  -> view club detail
  -> sign in or complete profile
  -> apply to a club
  -> president/admin reviews the request
  -> club data stays accurate on mobile and desktop
```

## Phase 0 - Documentation and Scope Lock

Goal:

- Make the repo purpose clear.
- Record the three-repo strategy.
- Keep future repos out of active scope.

Deliverables:

- README describes this as the 1st repo.
- Repo strategy document exists.
- Priority roadmap exists.
- Development sequence exists.

Exit check:

- A new maintainer can tell what to work on first without reading the Notes app.

## Phase 1 - Safety and Local Reliability

Goal:

Make the existing app safer and easier to run before adding major features.

Tasks:

1. Review backend permission helpers.
2. Add or strengthen tests for student/admin/president boundaries.
3. Remove unsafe defaults from config.
4. Document the local database setup.
5. Confirm image upload validation.
6. Run backend tests and frontend type checks.

Exit check:

- A student cannot edit club data.
- A non-manager cannot see pending requests or rosters.
- The app can be started locally from documented steps.

## Phase 2 - Student-Facing Core

Goal:

Make the public and logged-in student experience dependable.

Tasks:

1. Improve mobile layout for homepage, search, club detail, and profile.
2. Improve club keyword search.
3. Fix profile/user center content.
4. Add account setup fields needed for school use.
5. Add user agreements if required for account creation.

Exit check:

- A student can use the site from a phone.
- Search finds clubs by normal words students would type.
- A logged-in student understands their account, school, and club state.

## Phase 3 - Club President Workflow

Goal:

Let club presidents maintain their own clubs without giving them broad admin access.

Tasks:

1. Verify president role assignment behavior.
2. Strengthen club edit permissions.
3. Improve club edit page usability.
4. Improve pending request review flow.
5. Keep image upload local and validated.

Exit check:

- A president can edit only their assigned club.
- A president can approve or reject requests for that club.
- A president cannot manage another club.

## Phase 4 - School Admin Workflow

Goal:

Let school admins operate a school directory without platform-owner intervention.

Tasks:

1. Add club creation to the school admin page.
2. Add president assignment to the school admin page.
3. Make school admin navigation separate from normal student navigation.
4. Show useful school-level counts or warnings.
5. Keep platform owner controls separate from school admin controls.

Exit check:

- A school admin can create a club.
- A school admin can assign a president.
- A school admin can reach management pages without touching platform owner tools.

## Phase 5 - Visual Polish and Default Theme

Goal:

Apply the desired school theme after the functional pages are stable.

Tasks:

1. Move shared visual tokens into maintainable CSS variables.
2. Apply the default theme from the design PDF.
3. Improve homepage image logic using school/club data.
4. Check mobile and desktop layout after styling changes.

Exit check:

- The app looks consistent without hiding or breaking core actions.
- The design does not make admin or student workflows harder to scan.

## Phase 6 - Summary API for Future Repos

Goal:

Prepare the cleanest possible bridge to the future 2nd repo.

Tasks:

1. Define a public school summary response.
2. Include school identity, club counts, category counts, status, and update time.
3. Add a hash or checksum only if it helps the aggregator detect changes.
4. Document the endpoint in docs/API.md.
5. Add tests for the endpoint.

Exit check:

- A future aggregator can fetch useful data without database access.
- The endpoint does not expose private student or admin data.

## Phase 7 - Decide on 2nd Repo

Start the 2nd repo only after Phase 6 exits cleanly.

The 2nd repo should consume public summary data. It should not copy the club workflow, admin workflow, or auth model unless there is a strong reason.

## Phase 8 - Decide on 3rd Repo

Start the 3rd repo only after the responsive web app has been tested by real users.

Before creating a native app, consider whether a PWA or mobile-first web entry page is enough.

## Recommended Work Chunk Size

Keep each implementation chunk small enough to verify in one sitting:

- One page or one endpoint at a time.
- Tests for permission changes.
- Manual mobile smoke check for UI changes.
- Documentation update when a feature changes the development order or public API.

## What to Avoid for Now

Avoid these until the core loop is stable:

- Social media timelines.
- Complex recommendation algorithms.
- Native mobile development.
- Separate aggregator backend.
- Large visual rewrites before student/admin flows work.
