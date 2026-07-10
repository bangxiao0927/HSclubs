# Documentation Index

Use these docs in this order when planning work for the 1st repo.

## Active Planning Docs

1. [Repo Strategy](REPO_STRATEGY.md)
   - Records the original 1st, 2nd, and 3rd repo structure.
   - Explains why active work starts with the 1st repo.

2. [1st Repo Roadmap](FIRST_REPO_ROADMAP.md)
   - Prioritizes work from P0 through P4.
   - Converts the Notes plan into practical issue-sized areas.

3. [Development Sequence](DEVELOPMENT_SEQUENCE.md)
   - Gives the recommended build order.
   - Defines exit checks before moving to later phases or later repos.

4. [Execution Criteria](EXECUTION_CRITERIA.md)
   - Defines acceptance criteria, issue breakdown, and verification checklists.
   - Use this when turning roadmap items into development tasks.

## Reference Docs

- [API Reference](API.md) - current backend API shape. The **API Plan (P0.3)** section defines school-scoped routes as the primary path and lists compatibility-only legacy routes.
- [Deployment Notes](DEPLOYMENT.md) - environment and deployment setup.
- [Schema Migration](schema-migration.sql) - database migration reference.
- [Original Multi-School Plan](PLAN.md) - long-term platform background, not the current execution order.

## Maintenance Rules

- Update the active planning docs when project direction changes.
- Keep README short and focused on how this repo should be used now.
- Keep long-term platform ideas in reference docs until they become active work.
- Do not start the 2nd or 3rd repo until the exit checks in DEVELOPMENT_SEQUENCE.md say the 1st repo is ready.
- Prefer simple web features over high-maintenance social, native mobile, or distributed backend work.
- Keep the platform single-school first. Other schools should copy the pattern instead of sharing a multi-tenant platform.
- After completing a PR (merging/resolving conflicts), open a follow-up commit to self-review the changes and verify PR status checks pass before marking the PR as ready.
- All user-facing and documentation copy must remain in English only.
