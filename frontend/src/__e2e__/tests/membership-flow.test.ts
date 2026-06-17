import { describe, it, expect, beforeEach, beforeAll, afterAll, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import {
  fetchClubs, fetchClubById, applyToClub, cancelMembershipRequest,
  fetchMembershipRequests, approveMembershipRequest,
  rejectMembershipRequest, fetchClubMembers,
} from '../../services/clubService'

beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('Membership Flow', () => {
  it('student can browse clubs for a school', async () => {
    const clubs = await fetchClubs({ schoolSlug: 'mvhs' })
    expect(clubs).toHaveLength(2)
  })

  it('student can view club details', async () => {
    server.use(
      http.get('http://localhost:8080/api/schools/mvhs/clubs/1', () => {
        return HttpResponse.json({
          id: 1, name: 'Robotics Club', description: 'Build robots.',
          category: 'STEM & Innovation', meetingSchedule: 'Thursday · Weekly · Lunch',
          location: 'Room 301', advisor: 'Dr. Smith', memberCount: 25,
          achievements: [], schoolId: 1, status: 'active', visibility: 'public',
          viewerIsMember: false, viewerHasPendingRequest: false, canManage: false,
        })
      }),
    )
    const club = await fetchClubById(1, 'mvhs')
    expect(club.name).toBe('Robotics Club')
    expect(club.viewerIsMember).toBe(false)
  })

  it('student can apply to join a club', async () => {
    server.use(
      http.post('http://localhost:8080/api/schools/mvhs/clubs/1/members/apply',
        () => new HttpResponse(null, { status: 204 })),
    )
    await expect(applyToClub(1, 'mvhs')).resolves.toBeUndefined()
  })

  it('student can cancel application', async () => {
    server.use(
      http.delete('http://localhost:8080/api/schools/mvhs/clubs/1/members/apply',
        () => new HttpResponse(null, { status: 204 })),
    )
    await expect(cancelMembershipRequest(1, 'mvhs')).resolves.toBeUndefined()
  })

  it('admin views and processes membership requests', async () => {
    server.use(
      http.get('http://localhost:8080/api/schools/mvhs/clubs/1/membership-requests', () => {
        return HttpResponse.json([
          { id: 100, clubId: 1, oauthUserId: 10, displayName: 'New Student',
            email: 'new@mvhs.edu', createdAt: '2025-01-01', status: 'pending' },
        ])
      }),
      http.post('http://localhost:8080/api/schools/mvhs/clubs/1/membership-requests/100/approve',
        () => new HttpResponse(null, { status: 204 })),
      http.delete('http://localhost:8080/api/schools/mvhs/clubs/1/membership-requests/101',
        () => new HttpResponse(null, { status: 204 })),
    )
    const requests = await fetchMembershipRequests(1, 'mvhs')
    expect(requests).toHaveLength(1)
    expect(requests[0]!.status).toBe('pending')
    await expect(approveMembershipRequest(1, 100, 'mvhs')).resolves.toBeUndefined()
    await expect(rejectMembershipRequest(1, 101, 'mvhs')).resolves.toBeUndefined()
  })

  it('admin can view club members', async () => {
    server.use(
      http.get('http://localhost:8080/api/schools/mvhs/clubs/1/members', () => {
        return HttpResponse.json([
          { oauthUserId: 1, displayName: 'President', email: 'p@mvhs.edu', roleName: 'president' },
          { oauthUserId: 2, displayName: 'Member', email: 'm@mvhs.edu', roleName: 'member' },
        ])
      }),
    )
    const members = await fetchClubMembers(1, 'mvhs')
    expect(members).toHaveLength(2)
    expect(members[0]!.roleName).toBe('president')
  })
})
