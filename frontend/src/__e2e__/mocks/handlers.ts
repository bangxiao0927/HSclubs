import { http, HttpResponse } from 'msw'

const BASE = 'http://localhost:8080'

export const handlers = [
  http.get(`${BASE}/api/schools`, () => {
    return HttpResponse.json([
      { id: 1, slug: 'mvhs', schoolName: 'Mountain View High School', shortName: 'MVHS', status: 'active', timezone: 'America/Los_Angeles' },
      { id: 2, slug: 'pahs', schoolName: 'Palo Alto High School', shortName: 'Paly', status: 'active', timezone: 'America/Los_Angeles' },
    ])
  }),

  http.get(`${BASE}/api/schools/:slug`, ({ params }) => {
    if (params.slug === 'mvhs') {
      return HttpResponse.json({ id: 1, slug: 'mvhs', schoolName: 'Mountain View High School', shortName: 'MVHS', status: 'active', timezone: 'America/Los_Angeles' })
    }
    return new HttpResponse('Not Found', { status: 404 })
  }),

  http.get(`${BASE}/api/schools/:slug/clubs`, () => {
    return HttpResponse.json([
      { id: 1, name: 'Robotics Club', slug: 'robotics', description: 'Build robots.', category: 'STEM & Innovation', meetingSchedule: 'Thursday · Weekly · Lunch', location: 'Room 301', advisor: 'Dr. Smith', memberCount: 25, achievements: [], schoolId: 1, status: 'active', visibility: 'public' },
      { id: 2, name: 'Debate Team', slug: 'debate', description: 'Competitive debate.', category: 'Competition & Strategy', meetingSchedule: 'Tuesday · Weekly · Lunch', location: 'Room 210', advisor: 'Ms. Johnson', memberCount: 18, achievements: [], schoolId: 1, status: 'active', visibility: 'public' },
    ])
  }),

  http.get(`${BASE}/api/auth/me`, () => {
    return HttpResponse.json({
      id: 'google-123', email: 'student@mvhs.edu', displayName: 'Test Student',
      avatarUrl: 'https://api.dicebear.com/7.x/thumbs/svg?seed=Test',
      provider: 'google', isOwner: false, graduationYear: 2026,
      schoolMemberships: [{ schoolId: 1, slug: 'mvhs', schoolName: 'Mountain View High School', role: 'student', status: 'active' }],
    })
  }),

  http.post(`${BASE}/api/auth/logout`, () => new HttpResponse(null, { status: 204 })),
]
