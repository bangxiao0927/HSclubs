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
Return current authenticated user with school memberships.

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
      "role": "student",
      "status": "active"
    }
  ]
}
```

Status: 200 | 401 (not authenticated)

### POST /api/auth/logout
Invalidate session. Status: 204

---

## Schools

### GET /api/schools
List all active schools. Public.

### GET /api/schools/{slug}
Get school detail by slug. 404 if not found or inactive.

Response:
```json
{
  "id": 1,
  "slug": "mvhs",
  "schoolName": "Mountain View High School",
  "shortName": "MVHS",
  "logoUrl": "/school-assets/mvhs-logo.png",
  "bannerUrl": null,
  "primaryColor": "#facc15",
  "status": "active",
  "timezone": "America/Los_Angeles"
}
```

---

## School-Scoped Clubs (Primary)

All under `/api/schools/{schoolSlug}/clubs`. **This is the primary path for club data.** New frontend and backend code must target these routes.

### GET /api/schools/{schoolSlug}/clubs
List active clubs for a school. Public. Supports `page` and `size` query parameters for pagination.

### POST /api/schools/{schoolSlug}/clubs
Create a club. Requires: school_admin or platform_owner.

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
  "memberCount": 0,
  "schoolId": 1
}
```

Status: 201 | 400 | 403

### GET /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
Get club detail. Accepts numeric ID or slug string. Viewer permissions (viewerIsMember, canManage, viewerHasPendingRequest) included when authenticated.

### PUT /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
Update club. Requires: school_admin, platform_owner, or club president.

### DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}
Delete club. Requires: school_admin or platform_owner. Status: 204

---

## Membership

### GET /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members
List club members. Requires: school_admin, platform_owner, or club president.

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

### POST /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members/apply
Apply to join a club. Requires: authenticated.

Status: 204 | 400 | 401 | 409 (already applied/member)

### DELETE /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/members/apply
Cancel own membership application. Requires: authenticated.

---

## Membership Requests (Admin)

### GET /api/schools/{schoolSlug}/clubs/{clubSlugOrId}/membership-requests
List pending membership requests. Requires: school_admin, platform_owner, or club president.

### POST .../membership-requests/{requestId}/approve
Approve a membership request. Adds user as member. Status: 204

### DELETE .../membership-requests/{requestId}
Reject/delete a membership request. Status: 204

---

## Platform Admin

All under `/api/platform`. Requires: platform_owner.

### GET /api/platform/schools
List all schools (including inactive).

### POST /api/platform/schools
Create a new school.

Request body:
```json
{
  "slug": "pahs",
  "schoolName": "Palo Alto High School",
  "shortName": "Paly",
  "timezone": "America/Los_Angeles"
}
```

Status: 201 | 400

### PUT /api/platform/schools/{slug}
Update school information.

---

## Compatibility-Only (Deprecated) Endpoints

> ⚠️ **Deprecated. Do not use in new code.** These endpoints are retained only for backward compatibility with the original MVHS-only deployment. The corresponding backend controller is annotated `@deprecated` in `ClubController.java`. New clients should call the school-scoped routes listed above.

| Legacy Route | School-Scoped Replacement |
|--------------|---------------------------|
| `GET /api/clubs` | `GET /api/schools/{slug}/clubs` |
| `GET /api/clubs/{id}` | `GET /api/schools/{slug}/clubs/{id}` |
| `POST /api/clubs` | `POST /api/schools/{slug}/clubs` |
| `PUT /api/clubs/{id}` | `PUT /api/schools/{slug}/clubs/{id}` |
| `DELETE /api/clubs/{id}` | `DELETE /api/schools/{slug}/clubs/{id}` |
| `GET /api/clubs/{id}/members` | `GET /api/schools/{slug}/clubs/{id}/members` |
| `POST /api/clubs/{id}/members/apply` | `POST /api/schools/{slug}/clubs/{id}/members/apply` |
| `DELETE /api/clubs/{id}/members/apply` | `DELETE /api/schools/{slug}/clubs/{id}/members/apply` |
| `GET /api/clubs/{id}/membership-requests` | `GET /api/schools/{slug}/clubs/{id}/membership-requests` |
| `POST /api/clubs/{id}/membership-requests/{requestId}/approve` | `POST /api/schools/{slug}/clubs/{id}/membership-requests/{requestId}/approve` |
| `DELETE /api/clubs/{id}/membership-requests/{requestId}` | `DELETE /api/schools/{slug}/clubs/{id}/membership-requests/{requestId}` |

**Removal plan:** these routes will be removed once the 1st repo roadmap is complete, all known clients are migrated to school-scoped paths, and the deprecation is announced in a release note. Bug fixes will still be accepted for the duration of the deprecation period.

## Future API Needs (Track Before Implementing)

The following endpoints have been identified as likely needs. **Do not build them yet**; this list exists so the next person does not accidentally invent a duplicate or contradictory shape. When one of these is picked up, design the request/response shape first and add it to this section.

- `GET /api/schools/{slug}/summary` — landing-page summary for a school: total active clubs, counts by category, total pending membership requests visible to the viewer, upcoming events count.
- `GET /api/schools/{slug}/clubs/{id}/summary` — club detail summary: member count, pending request count, recent activity, advisor.
- `GET /api/schools/{slug}/clubs/{id}/events` — recurring schedule and one-off events for a club.
- `GET /api/me/summary` — user-centered summary: home school, active memberships, pending applications, unread notifications.
- `GET /api/schools/{slug}/members/search` — admin-only search across a school's members (for roster management).
- `GET /api/platform/summary` — platform-owner landing summary: total schools, active clubs, pending invitations.

Before any of these are implemented, the proposer should:

1. Confirm no school-scoped, platform, or global endpoint already covers the use case.
2. Sketch the request and response shape in `docs/API.md` and request review.
3. Add the route under the appropriate primary section (school-scoped, platform, or global profile), **not** under compatibility-only.

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
