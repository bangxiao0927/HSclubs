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

| Code | Meaning |
|------|---------|
| 400 | Bad request (validation) |
| 401 | Not authenticated |
| 403 | Permission denied |
| 404 | Resource not found |
| 409 | Conflict (duplicate request) |
| 500 | Internal server error |
