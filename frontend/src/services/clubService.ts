import type { Club, ClubMember, ClubMembershipRequest } from '../types/club'
import { buildApiUrl } from './httpClient'

type FetchClubsOptions = {
  force?: boolean
}

const CLUBS_CACHE_TTL_MS = 5 * 60 * 1000

let clubsCache: Club[] | null = null
let clubsCacheExpiresAt = 0
let clubsRequest: Promise<Club[]> | null = null

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const headers = new Headers(init?.headers as HeadersInit | undefined)
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(url, {
    ...init,
    credentials: init?.credentials ?? 'include',
    headers,
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  const raw = await response.text()
  if (!raw) {
    return undefined as T
  }
  return JSON.parse(raw) as T
}

const cloneClubs = (clubs: Club[]) => clubs.map((club) => ({ ...club }))

const setClubsCache = (clubs: Club[]) => {
  clubsCache = cloneClubs(clubs)
  clubsCacheExpiresAt = Date.now() + CLUBS_CACHE_TTL_MS
}

export const invalidateClubCache = () => {
  clubsCache = null
  clubsCacheExpiresAt = 0
  clubsRequest = null
}

export const fetchClubs = async (options: FetchClubsOptions = {}) => {
  const { force = false } = options
  const now = Date.now()

  if (!force && clubsCache && now < clubsCacheExpiresAt) {
    return cloneClubs(clubsCache)
  }

  if (!force && clubsRequest) {
    return cloneClubs(await clubsRequest)
  }

  clubsRequest = request<Club[]>('/api/clubs')

  try {
    const clubs = await clubsRequest
    setClubsCache(clubs)
    return cloneClubs(clubs)
  } finally {
    clubsRequest = null
  }
}

export const fetchClubById = (id: number | string) => request<Club>(`/api/clubs/${id}`)

export const updateClub = async (id: number | string, data: Partial<Club>) => {
  const updatedClub = await request<Club>(`/api/clubs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })

  if (clubsCache) {
    const nextClubs = clubsCache.map((club) => (String(club.id) === String(updatedClub.id) ? { ...club, ...updatedClub } : club))
    setClubsCache(nextClubs)
  }

  return updatedClub
}

export const fetchClubMembers = (id: number | string) => request<ClubMember[]>(`/api/clubs/${id}/members`)

export const applyToClub = (id: number | string) =>
  request<void>(`/api/clubs/${id}/members/apply`, {
    method: 'POST',
    body: JSON.stringify({}),
  })

export const cancelMembershipRequest = (id: number | string) =>
  request<void>(`/api/clubs/${id}/members/apply`, {
    method: 'DELETE',
  })

export const fetchMembershipRequests = (id: number | string) =>
  request<ClubMembershipRequest[]>(`/api/clubs/${id}/membership-requests`)

export const approveMembershipRequest = (clubId: number | string, requestId: number | string) =>
  request<void>(`/api/clubs/${clubId}/membership-requests/${requestId}/approve`, {
    method: 'POST',
    body: JSON.stringify({}),
  })

export const rejectMembershipRequest = (clubId: number | string, requestId: number | string) =>
  request<void>(`/api/clubs/${clubId}/membership-requests/${requestId}`, {
    method: 'DELETE',
  })
