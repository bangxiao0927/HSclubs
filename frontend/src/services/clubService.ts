import type { Club, ClubMember, ClubMembershipRequest } from '../types/club'
import { buildApiUrl } from './httpClient'

type FetchClubsOptions = {
  force?: boolean
  schoolSlug?: string
}

const CLUBS_CACHE_TTL_MS = 5 * 60 * 1000

let clubsCache: Club[] | null = null
let clubsCacheKey = ''
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
  if (!raw) return undefined as T
  return JSON.parse(raw) as T
}

const cloneClubs = (clubs: Club[]) => clubs.map((club) => ({ ...club }))

const setClubsCache = (key: string, clubs: Club[]) => {
  clubsCache = cloneClubs(clubs)
  clubsCacheKey = key
  clubsCacheExpiresAt = Date.now() + CLUBS_CACHE_TTL_MS
}

export const invalidateClubCache = () => {
  clubsCache = null
  clubsCacheKey = ''
  clubsCacheExpiresAt = 0
  clubsRequest = null
}

const clubPath = (schoolSlug: string | undefined, suffix: string) => {
  if (schoolSlug) {
    return `/api/schools/${schoolSlug}/clubs${suffix}`
  }
  return `/api/clubs${suffix}`
}

export const fetchClubs = async (options: FetchClubsOptions = {}) => {
  const { force = false, schoolSlug } = options
  const cacheKey = schoolSlug ?? '__global__'
  const now = Date.now()

  if (!force && clubsCache && cacheKey === clubsCacheKey && now < clubsCacheExpiresAt) {
    return cloneClubs(clubsCache)
  }

  if (!force && clubsRequest) {
    return cloneClubs(await clubsRequest)
  }

  clubsRequest = request<Club[]>(clubPath(schoolSlug, ''))

  try {
    const clubs = await clubsRequest
    setClubsCache(cacheKey, clubs)
    return cloneClubs(clubs)
  } finally {
    clubsRequest = null
  }
}

export const fetchClubById = (id: number | string, schoolSlug?: string) =>
  request<Club>(clubPath(schoolSlug, `/${id}`))

export const updateClub = async (
  id: number | string,
  data: Partial<Club>,
  schoolSlug?: string,
) => {
  const updatedClub = await request<Club>(clubPath(schoolSlug, `/${id}`), {
    method: 'PUT',
    body: JSON.stringify(data),
  })
  if (clubsCache) {
    const nextClubs = clubsCache.map((club) =>
      String(club.id) === String(updatedClub.id) ? { ...club, ...updatedClub } : club,
    )
    setClubsCache(clubsCacheKey, nextClubs)
  }
  return updatedClub
}

export const fetchClubMembers = (id: number | string, schoolSlug?: string) =>
  request<ClubMember[]>(clubPath(schoolSlug, `/${id}/members`))

export const applyToClub = (id: number | string, schoolSlug?: string) =>
  request<void>(clubPath(schoolSlug, `/${id}/members/apply`), {
    method: 'POST',
    body: JSON.stringify({}),
  })

export const cancelMembershipRequest = (id: number | string, schoolSlug?: string) =>
  request<void>(clubPath(schoolSlug, `/${id}/members/apply`), {
    method: 'DELETE',
  })

export const fetchMembershipRequests = (id: number | string, schoolSlug?: string) =>
  request<ClubMembershipRequest[]>(
    clubPath(schoolSlug, `/${id}/membership-requests`),
  )

export const approveMembershipRequest = (
  clubId: number | string,
  requestId: number | string,
  schoolSlug?: string,
) =>
  request<void>(
    clubPath(schoolSlug, `/${clubId}/membership-requests/${requestId}/approve`),
    { method: 'POST', body: JSON.stringify({}) },
  )

export const rejectMembershipRequest = (
  clubId: number | string,
  requestId: number | string,
  schoolSlug?: string,
) =>
  request<void>(clubPath(schoolSlug, `/${clubId}/membership-requests/${requestId}`), {
    method: 'DELETE',
  })
