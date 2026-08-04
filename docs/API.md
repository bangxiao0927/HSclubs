# HSclubs API Reference

Base URL: `http://localhost:8080`

## API Plan (P0.3)

This API serves a single-school club directory. All club endpoints live under `/api/clubs`.


## Summary API (Aggregator)

### GET /api/summary
Public endpoint consumed by the 2nd-repo aggregator. Returns club directory stats.

Response:
```json
{
  "schoolName": "HS Clubs",
  "shortName": "HS Clubs",
  "slug": "hsclubs",
  "status": "active",
  "clubCount": 42,
  "categories": { "STEM & Innovation": 15, "Creative Arts & Media": 8 },
  "memberCount": 350,
  "lastUpdatedAt": "2025-07-01T12:00:00",
  "dataHash": "abc123..."
}
```

The `dataHash` is a SHA-256 digest of (clubId|name|category|memberCount) for all clubs. The aggregator compares this hash to detect changes without re-fetching.

## Authentication

All endpoints use session-based auth via Spring Security OAuth2. Include credentials (`credentials: 'include'`) in client requests.

### GET /api/auth/providers
List available OAuth providers.

Response: `[{ "id": "google", "name": "Google", "authorizationUrl": "/api/auth/authorize/google" }]`

### GET /api/auth/me
Return current authenticated user profile.

Response:
```json
{
  "id": "google-123",
  "email": "maya.chen@example.com",
  "displayName": "Maya Chen",
  "avatarUrl": "https://...",
  "provider": "google",
  "graduationYear": 2026,
  "isOwner": false,
}
```

Status: 200 | 401 (not authenticated)

### POST /api/auth/logout
Invalidate session. Status: 204

---

---

## Clubs (Primary)

All club endpoints live under `/api/clubs`. This is the single-school API pattern.

### GET /api/clubs
List active clubs. Public. Supports `page` and `size` query parameters for pagination.

### POST /api/clubs
Create a club. Requires: platform_owner.

Request body:
```json
{
  "name": "Robotics Club",
  "slug": "robotics",
  "description": "Build robots and compete.",
  "category": "STEM & Innovation",
  "meetingSchedule": "Thursday · Weekly · Lunch",
  "location": "Room 301",
  "contactEmail": "robotics@school.edu",
  "advisor": "Dr. Smith",
  "memberCount": 0
}
```

Status: 201 | 400 | 403

### GET /api/clubs/{clubSlugOrId}
Get club detail. Accepts numeric ID or slug string. Viewer permissions (viewerIsMember, canManage, viewerHasPendingRequest) included when authenticated.

### PUT /api/clubs/{clubSlugOrId}
Update club. Requires: platform_owner, or club president.

### DELETE /api/clubs/{clubSlugOrId}
Delete club. Requires: platform_owner. Status: 204

---

## Club Media (Posts and Comments)

A per-club photo feed: members publish a post (one photo plus a title), anyone can read the
public feed and its comments, and members can comment. See
`docs/FIRST_REPO_ROADMAP.md` item 23 for why this shipped and which moderation/storage/
deletion/abuse concerns it addresses (rate limiting on publishing is still open -- see
`docs/ISSUES.md`).

All eight endpoints below live under `/api/clubs`, following the same primary-route
convention as the rest of this section (see the "Future API Needs" preamble later in this
file for how new routes should be proposed).

**Photo URLs are unauthenticated, unguessable capability URLs, not authorization-checked
resources.** A post's own `imageUrl`, under `/uploads/club-posts/<uuid>.jpg`, is served by
the static `/uploads/**` resource handler (`WebConfig`), which sits under
Spring Security's `anyRequest().permitAll()` -- it does not check `clubs.status`,
`ClubVisibilityPolicy`, or authentication at all. The feed and comment endpoints below are
gated (a non-active club's feed is hidden from non-members), but once a client has a photo
URL -- from the feed, from a shared link, or by guessing -- nothing stops it from being
fetched directly, by anyone, forever (until the post is deleted). Security rests entirely on
the UUIDv4 filename being unguessable, the same model as an unlisted document link. This is a
deliberate trade-off: serving images through an authorization-aware controller instead would
also have to cover club cover images, and would give up static file serving and HTTP caching.

`authorAvatarUrl` is not part of this boundary: it is `oauth_users.avatar_url`, populated
from the OAuth provider's own profile picture claim (e.g. Google's `picture`) at login, and
is an external URL on the provider's own domain, not a file under `/uploads/**`. It can be
`null` when the provider did not supply one.

A `PublicClubPost` (returned by publish and by the feed) looks like:
```json
{
  "id": 12,
  "clubId": 3,
  "title": "Robotics build day",
  "imageUrl": "/uploads/club-posts/3f9c1b2a-3e3d-4a2e-9a8b-2b6b7d5e1a11.jpg",
  "pinnedAt": null,
  "createdAt": "2026-08-03T18:42:11Z",
  "authorDisplayName": "Maya Chen",
  "authorAvatarUrl": "https://...",
  "commentCount": 4,
  "viewerCanDelete": false
}
```
`viewerCanDelete` is computed server-side from the caller's own identity and the club's
moderation state; it is never the author's or viewer's own user ID. Never present:
`authorOauthUserId`, the author's email, or any other account field.

A `PublicClubPostComment` (returned by comment creation and by the comment list) looks like:
```json
{
  "id": 5,
  "postId": 12,
  "body": "Great turnout today!",
  "createdAt": "2026-08-03T19:02:44Z",
  "authorDisplayName": "Maya Chen",
  "authorAvatarUrl": "https://...",
  "viewerCanDelete": true
}
```

### POST /api/clubs/{clubSlugOrId}/posts
Publish a post: one photo plus a title. Requires: authenticated club member.

Request: `multipart/form-data` with fields `title` (string, 1-140 characters after trimming)
and `file` (the image). This is one atomic request; there is no separate "upload the photo,
then create the post" step, so a post can never reference a photo that was never actually
stored.

The file is validated and re-encoded by `ImageStorageService` before anything is written to
the database: JPEG/PNG/WebP are flattened, EXIF-stripped, and re-encoded to JPEG (capped at
1600px on the long edge); GIF passes through unchanged to preserve animation. Size limits:
5MB for JPEG/PNG/WebP, 2MB for GIF. Both the format and these limits are enforced from the
file's sniffed magic bytes, never the client-declared `Content-Type`.

These are application-level limits, checked by `ImageStorageService` only after the request
has already cleared a stricter, earlier ceiling: Spring's own
`spring.servlet.multipart.max-file-size` (6MB per part) and `max-request-size` (8MB total).
A file larger than that multipart ceiling never reaches `ImageStorageService` at all -- Spring
rejects it first with a 413, before the title is even read, so it cannot also produce one of
the 400s below. The two limits are deliberately layered (6MB above the application's own 5MB
JPEG/PNG/WebP limit): if they were equal, Spring would take the file before the readable
400 could ever be produced, turning that validation into dead code.

That 413 body -- and every 400/403/404 status below -- is a real `application/problem+json`
document (verified end to end by `ApiExceptionHandlerIntegrationTest`, not asserted from
reading the handler alone), written by `ApiExceptionHandler`:
```json
{
  "title": "Content Too Large",
  "status": 413,
  "detail": "The uploaded file is too large. Please choose a smaller file and try again.",
  "instance": "/api/clubs/42/posts"
}
```
`title` is the status's own HTTP reason phrase (`HttpStatus.CONTENT_TOO_LARGE`'s "Content Too
Large" for 413; for every other status below, whatever `ResponseStatusException` derives from
the thrown `HttpStatus`), not chosen by either handler method. `detail` is the exact English
message the throwing code passed: the multipart handler's own fixed string for 413, or, for
every 400/403/404 below, the literal `reason` argument the controller/service passed to
`new ResponseStatusException(status, reason)` (e.g. `"Title must be 140 characters or fewer"`).
`instance` is filled in by Spring's own `ProblemDetail` return-value handling from the
request's actual path, not by either handler -- expect the real path hit (e.g.
`/api/clubs/42/posts`), not a fixed placeholder. `type` is a fifth field `ProblemDetail`
supports, but neither handler method ever sets one: an unset `type` is omitted from the JSON
entirely (Jackson never writes it, not even as `null` or the RFC 9457 default of
`"about:blank"`) -- do not expect a `type` key in any of these response bodies.

Response: 201 with a `PublicClubPost` body (shape above).

Status: 201 | 400 (missing/empty title or file, title over 140 characters, unsupported or
corrupt image, or image over its *application-level* format size or resolution limit -- see
above) | 403 (authenticated but not a member of this club) | 404 (club not found) | 413 (the
file or overall request exceeded Spring's multipart ceiling before any application code ran --
see "Error Responses" below) | 500 (rare: the post could not be read back immediately after
being written, or the file could not be stored)

### GET /api/clubs/{clubSlugOrId}/posts
Read a club's public post feed, newest and pinned-first. Public; no authentication required,
but an authenticated viewer's own capabilities (see `viewerCanDelete` above) are reflected in
the response.

Visibility follows `ClubVisibilityPolicy`, the same policy the comment list below uses: an
`active` club's feed is visible to anyone; a non-`active` club's feed is visible only to a
member of that club, its president, or a platform owner. A club that exists but is not
visible to the caller returns 404, the same as a club that does not exist at all, so its
existence is not leaked.

Query params: `page` (default `0`) and `size` (default `12`).

Response envelope:
```json
{
  "items": [ /* PublicClubPost */ ],
  "page": 0,
  "size": 12,
  "total": 37
}
```
`page` and `size` in the response are clamped, not the raw request values: `size` is clamped
to between 1 and 100, a negative `page` is clamped to `0`, and both echoed values are what was
actually served. A client computing `ceil(total / size)` from the response must use these
clamped values, not whatever it originally requested, or it can be misled about how many
pages exist.

Status: 200 | 404 (club not found, or not visible to this caller)

### PUT /api/clubs/{clubSlugOrId}/posts/{postId}/pin
Pin a post to the front of the feed. Requires: the club's own president, or a platform owner
(pinning is an editorial power, not a publishing one -- the post's own author is not enough
on its own). At most 3 posts can be pinned per club at a time.

Status: 204 | 403 (not the club's president or a platform owner) | 404 (club or post not
found) | 409 (3 posts already pinned, or a concurrent pin attempt on the same club timed out
the lock -- both mean "try again", not a server error)

### DELETE /api/clubs/{clubSlugOrId}/posts/{postId}/pin
Unpin a post. Same authorization as pin above.

Status: 204 | 403 | 404 (club or post not found)

### DELETE /api/clubs/{clubSlugOrId}/posts/{postId}
Delete a post: removes the row, its photo file on disk, and its comments (cascade). Requires:
the post's own author, the club's president, or a platform owner (the same moderation matrix
`ClubContentModerationPolicy` applies to comment deletion below).

Status: 204 | 403 (authenticated but not the author/president/owner) | 404 (club or post not
found) | 500 (rare: the row was already gone by the time the delete ran)

### GET /api/clubs/{clubSlugOrId}/posts/{postId}/comments
Read a post's comments, oldest first. Public; same `ClubVisibilityPolicy` gating as the feed
above, so a comment thread is never visible where the post itself would not be.

Response: a plain JSON array of `PublicClubPostComment` (shape above); not paginated or
wrapped in an envelope.

Status: 200 | 404 (club not found or not visible, or post not found in this club)

### POST /api/clubs/{clubSlugOrId}/posts/{postId}/comments
Post a comment. Requires: authenticated club member.

Request body:
```json
{ "body": "Great turnout today!" }
```
`body` must be 1-300 characters after trimming.

Response: 201 with a `PublicClubPostComment` body (shape above).

Status: 201 | 400 (missing/empty body, or over 300 characters) | 403 (not a member) | 404
(club or post not found) | 409 (post already has 50 comments -- the per-post cap -- or a
concurrent comment on the same post timed out the lock; both mean "try again")

### DELETE /api/clubs/{clubSlugOrId}/posts/{postId}/comments/{commentId}
Delete a comment. Requires: the comment's own author, the club's president, or a platform
owner.

Status: 204 | 403 | 404 (club not found, post not found in this club, or comment not found)

---

## Membership

### GET /api/clubs/{id}/members
List club members. Requires: platform_owner, or club president.

Response:
```json
[
  {
    "oauthUserId": 1,
    "displayName": "Maya Chen",
    "email": "maya.chen@example.com",
    "avatarUrl": "https://...",
    "roleName": "president"
  }
]
```

### POST /api/clubs/{id}/members/apply
Apply to join a club. Requires: authenticated.

Status: 204 | 400 | 401 | 409 (already applied/member)

### DELETE /api/clubs/{id}/members/apply
Cancel own membership application. Requires: authenticated.

---

## Membership Requests (Admin)

### GET /api/clubs/{id}/membership-requests
List pending membership requests. Requires: platform_owner, or club president.

### POST /api/clubs/{id}/membership-requests/{requestId}/approve
Approve a membership request. Adds user as member. Status: 204

### DELETE /api/clubs/{id}/membership-requests/{requestId}
Reject/delete a membership request. Status: 204



## Route Summary

All primary club routes use the `/api/clubs` pattern. There are no school-scoped route variants.

## Future API Needs (Track Before Implementing)

The following endpoints have been identified as likely needs. **Do not build them yet**; this list exists so the next person does not accidentally invent a duplicate or contradictory shape. When one of these is picked up, design the request/response shape first and add it to this section.

- `GET /api/clubs/summary` — directory-wide summary: total active clubs, counts by category, upcoming events count.
- `GET /api/clubs/{id}/summary` — club detail summary: member count, pending request count, recent activity, advisor.
- `GET /api/clubs/{id}/events` — recurring schedule and one-off events for a club.
- `GET /api/me/summary` — user-centered summary: active memberships, pending applications, unread notifications.
- `GET /api/users/search` — platform-owner search across members (for roster management).
- `GET /api/platform/stats` — platform-owner landing stats: total clubs, active users, pending requests.
- `GET /api/platform/owners` — manage platform owners (post/delete by email).

Before any of these are implemented, the proposer should:

1. Confirm no existing endpoint already covers the use case.
2. Sketch the request and response shape in `docs/API.md` and request review.
3. Add the route under the appropriate primary section (Clubs, Platform, or Profile).

---

## Error Responses

Every 400/403/404/409/413 body below is `application/problem+json` with `title`/`status`/
`detail`/`instance` (see the publish endpoint's own section above for the exact shape,
including why `type` is never present), written by `ApiExceptionHandler` from either a
`ResponseStatusException` the throwing controller/service raised itself, or, for 413 only,
Spring's own multipart-size rejection. **401 is the one exception:** it is Spring Security's
own `HttpStatusEntryPoint`, which runs entirely outside `DispatcherServlet` (before a request
is even routed to a controller) and calls only `HttpServletResponse#setStatus` -- a bare status
with no body of any kind, not `application/problem+json`, not plain text, nothing.
`ApiExceptionHandler` deliberately never touches this, or any other Spring Security
authentication/authorization rejection -- see that class's own Javadoc for why.

| Code | Meaning |
|------|---------|
| 400 | Bad request (validation). `detail` is the exact validation message, e.g. "Title must be 140 characters or fewer". |
| 401 | Not authenticated. Bare status, no body -- see above. |
| 403 | Permission denied. `detail` is the exact reason, e.g. "You do not have access to delete this post". |
| 404 | Resource not found. `detail` is the exact reason, e.g. "Club not found". |
| 409 | Conflict: a duplicate request, or a concurrency cap already reached. `detail` is the exact reason, e.g. "At most 3 posts can be pinned. Unpin one first.". |
| 413 | Any multipart upload (e.g. `POST /api/clubs/{clubSlugOrId}/posts` above) exceeded `spring.servlet.multipart.max-file-size` (6MB per part) or `max-request-size` (8MB total). A request whose raw body exceeds `server.tomcat.max-swallow-size` (10MB) may instead have its connection aborted before this response body can be delivered. |
| 500 | Internal server error |
