# HSclubs Repo Strategy

This document records the original three-repo plan and the current decision about development order.

## Decision

Work on the 1st repo first.

The 1st repo must become the reference implementation for one school before the project creates a separate aggregator repo or mobile repo. This keeps the work useful quickly, avoids duplicated systems, and lowers maintenance cost for a volunteer school project.

Project rules:

- Keep the repo focused on a single-school site. Other schools should copy the pattern instead of sharing a multi-tenant platform.
- All user-facing and documentation copy must remain in English only.
- After completing a PR (merging/resolving conflicts), open a follow-up commit to self-review the changes and verify PR status checks pass before marking the PR as ready.

## Original Three-Repo Structure

### 1st Repo - Public Sample School Site

Purpose:

- Keep the original repo public.
- Improve the current school club site until it is good enough for real users.
- Use it as the sample implementation for other schools.
- Prove the basic school workflow before building aggregation or mobile layers.

The 1st repo owns:

- School homepage.
- Club directory and search.
- Club detail pages.
- Login and account profile basics.
- Club application flow.
- President/admin workflows.
- Permissions and safety checks.
- Mobile-friendly web UI.
- A later public summary API that other repos can consume.

### 2nd Repo - Public Frontend, Private Backend

Purpose:

- Create a separate summary layer for multiple independent single-school sites after the sample repo is stable.
- Keep the frontend public.
- Keep the backend private if it collects status, verification, or operational data.
- Display all verified schools and their summary data.
- Connect school instances without making the 1st repo more complex than necessary.

The 2nd repo should not duplicate the club management system. It should consume public summaries from each 1st repo instance or from a small verified registry.

Likely future responsibilities:

- Platform homepage for all schools.
- School search.
- School map or region filter.
- Summary cards for each school.
- Backend status collection.
- Verification list for approved school sites.

### 3rd Repo - Public Mobile Entry Point

Purpose:

- Create a mobile app or mobile-first entry point after the web workflow is already strong.
- Let users choose or switch schools from a phone.
- Connect to the school instances instead of rebuilding their backend logic.

The 3rd repo depends on the 1st repo being phone-friendly. If the 1st repo already works well as a responsive web app or PWA, a native app can wait.

Likely future responsibilities:

- First-page school selection.
- Search or toggle to join/select a school.
- Floating school switcher.
- Login persistence.
- Deep links into the selected school's club pages.

## Creation Order

```text
1st repo: build the sample school site
    -> make the core school workflow stable
    -> add a public summary API when useful

2nd repo: build the optional aggregator for separate single-school sites
    -> consume summaries from verified school sites
    -> display and search schools

3rd repo: build mobile distribution or app
    -> reuse the 1st repo workflow
    -> switch schools from a mobile-first surface
```

## Why 1st Repo Comes First

The 2nd repo needs reliable data from real school sites. The 3rd repo needs a mobile-friendly school experience to point to. Without a stable 1st repo, both later repos would either duplicate logic or be forced to design around unstable data.

## Current Boundary

Do not create the 2nd or 3rd repo yet. Track their requirements here and in the roadmap, but keep active implementation inside this repo until the sample school workflow is dependable.
