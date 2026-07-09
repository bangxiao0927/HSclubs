# 1st Repo Roadmap

This roadmap turns the planning note into a practical order for the first public repo. The filter is:

1. Useful for real school users.
2. Short development cycle.
3. Low maintenance cost.
4. Builds the foundation for later repos without starting them too early.

## Project Rules

- Keep the repo focused on a single-school site. Other schools should copy the pattern instead of sharing a multi-tenant platform.
- All user-facing and documentation copy must remain in English only.
- After completing a PR (merging/resolving conflicts), open a follow-up commit to self-review the changes and verify PR status checks pass before marking the PR as ready.

## P0 - Stabilize the Sample School Site

These items should happen first because they protect the core workflow and reduce future cleanup.

### 1. Review security and permissions

Scope:

- Students must not edit clubs.
- Non-admin users must not view rosters or pending requests.
- Club presidents should only manage clubs they are assigned to.
- School admins and platform owners need clear boundaries.
- Uploaded files should be image-only and path-safe.

Reason:

Permissions are harder to fix after users begin relying on the system.

### 2. Make local development database setup simple

Scope:

- Keep MySQL usable for normal development.
- Keep H2 usable for tests.
- Remove unsafe defaults from configuration docs and runtime config.
- Document the minimum local environment needed to run the app.

Reason:

A school project needs a setup that future maintainers can run without guessing.

### 3. Confirm the API plan before adding new systems

Scope:

- Treat school-scoped APIs as the main path.
- Keep legacy APIs only for compatibility.
- Avoid adding a second API shape for the same workflow.
- Identify the future summary API needed by the 2nd repo.

Reason:

The current API already supports the single-school workflow. The priority is consistency, not more endpoints.

### 4. Fix mobile layout and phone usability

Scope:

- Homepage.
- School page.
- Club search.
- Club detail.
- Profile.
- Club admin.
- Pending membership requests.

Reason:

A responsive web app gives most of the value of the future mobile repo with far lower maintenance cost.

### 5. Improve club search

Scope:

- Search by club name.
- Search by alias.
- Search by category.
- Search by advisor/contact if useful.
- Support general keyword matching.

Reason:

Search is one of the highest-value student-facing features and is smaller than building new social systems.

### 6. Fix the user center/profile page

Scope:

- Show account identity.
- Show home school.
- Show graduation year.
- Show memberships or application state when available.
- Link to relevant school and club pages.

Reason:

The profile page is the user's anchor after login.

## P1 - Complete the School Workflow

These items turn the sample site into a fuller operating tool for club leaders and admins.

### 7. Add account creation data collection

Scope:

- Collect home school.
- Collect graduation year.
- Collect basic interest categories if recommendation work will use them.

Reason:

This supports personalization later without requiring a complex algorithm now.

### 8. Add user agreements

Scope:

- Terms of use.
- Privacy notes.
- Community expectations.
- Simple consent step during account setup if needed.

Reason:

Low development cost and useful for a school-facing platform.

### 9. Strengthen president edit pages and permissions

Scope:

- President can edit assigned club details.
- President can view and process that club's requests.
- President cannot manage unrelated clubs.

Reason:

This is central to the school workflow.

### 10. Add club creation in admin

Scope:

- School admin can create a club.
- Required fields are clear and minimal.
- New club appears in search and school pages.

Reason:

Admin creation is needed before other schools can operate independently.

### 11. Add president assignment in admin

Scope:

- School admin can assign a user as club president.
- Assignment updates permissions immediately.
- Existing president role behavior is reused.

Reason:

This unlocks delegated club maintenance.

### 12. Keep club image storage local and simple

Scope:

- Store uploaded club images locally for now.
- Validate file type and size.
- Use predictable URLs through the backend.
- Avoid cloud storage until the project needs it.

Reason:

Local storage is easier for a sample repo and keeps infrastructure small.

### 13. Improve homepage image logic

Scope:

- Prefer school and club image data already in the database.
- Avoid hard-coded image dependencies where possible.
- Keep fallback images simple.

Reason:

This improves visual quality without creating a content management system.

### 14. Apply the default theme from the design PDF

Scope:

- Update colors, spacing, and visual language after core flows are stable.
- Keep the theme maintainable through shared CSS variables.

Reason:

Design polish matters, but it should follow the functional workflow.

## P2 - Prepare for Optional Future Repos (Do Not Start Yet)

These items only apply after the single-school workflow is stable. They describe optional summaries or discovery work for other schools, but they do not make this repo multi-tenant.

### 15. Add public summary data API

Scope:

- School identity.
- School status.
- Club count.
- Category counts.
- Last updated timestamp.
- Optional hash/checksum for changed data.

Reason:

The 2nd repo should consume summaries instead of scraping or duplicating database access.

### 16. Strengthen platform home data display

Scope:

- Show available schools.
- Show simple aggregate counts.
- Link to school-specific pages.

Reason:

The current repo already has platform views. Improve them only as much as needed before creating a separate repo.

### 17. Add school search

Scope:

- Search by school name.
- Search by short name.
- Search by slug or region when available.

Reason:

School search is cheaper and more useful than a map as the first multi-school discovery feature.

### 18. Add school map later

Scope:

- Consider a US map only after school search and summary data are stable.

Reason:

A map is visually nice but higher maintenance than a searchable list.

## P3 - Later Engagement Features

These features can be valuable, but they expand moderation, storage, and product complexity.

### 19. Comments and ratings

Start only after login, club permissions, and moderation expectations are clear.

### 20. Recommendation page

Start with a simple rules-based version using interests and categories. Avoid complex algorithms until there is real usage data.

### 21. QR code club applications

Useful for events, but it should reuse a stable application flow.

### 22. Meeting attendance requests and checks

Useful for presidents, but it needs careful data modeling and should come after membership workflows are dependable.

## P4 - Defer

### 23. Media area and media timeline

Defer this work. A social feed or story-like timeline creates moderation, storage, abuse, and deletion requirements. It is not the right first feature for a low-maintenance school project.

### 24. 2nd repo creation

Do not create it until this repo has a stable public summary API and at least one school workflow worth aggregating.

### 25. 3rd repo creation

Do not create it until the responsive web experience is strong. A PWA or mobile-friendly web app may remove the need for a native app at first.
