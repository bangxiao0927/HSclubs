# HSclubs API Reference

Base URL: `http://localhost:8080`

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

## School-Scoped Clubs

All under `/api/schools/{schoolSlug}/clubs`

### GET /api/schools/{schoolSlug}/clubs
List active clubs for a school. Public.

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

## Legacy Endpoints

Preserved for backward compatibility:

| Legacy | School-Scoped Equivalent |
|--------|-------------------------|
| `GET /api/clubs` | `GET /api/schools/{slug}/clubs` |
| `GET /api/clubs/{id}` | `GET /api/schools/{slug}/clubs/{id}` |
| `POST /api/clubs` | `POST /api/schools/{slug}/clubs` |
| `PUT /api/clubs/{id}` | `PUT /api/schools/{slug}/clubs/{id}` |
| `DELETE /api/clubs/{id}` | `DELETE /api/schools/{slug}/clubs/{id}` |

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
