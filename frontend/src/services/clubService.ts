import type { Club, ClubMember, ClubMembershipRequest } from '../types/club'
import { buildApiUrl } from './httpClient'

type FetchClubsOptions = {
  force?: boolean
  page?: number
  size?: number
}

const CLUBS_CACHE_TTL_MS = 5 * 60 * 1000
const ALL_CLUBS_PAGE_SIZE = 100

let clubsCache: Club[] | null = null
let clubsCacheKey = ''
let clubsCacheExpiresAt = 0
let clubsRequest: Promise<Club[]> | null = null
let clubsRequestKey = ''
let allClubsCache: Club[] | null = null
let allClubsCacheExpiresAt = 0
let allClubsRequest: Promise<Club[]> | null = null

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const headers = new Headers(init?.headers as HeadersInit | undefined)
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(url, {
    ...init,
    credentials: init?.credentials ?? 'include',
    headers})
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
  clubsRequestKey = ''
  allClubsCache = null
  allClubsCacheExpiresAt = 0
  allClubsRequest = null
}

const clubPath = (suffix: string, page?: number, size?: number) => {
  let path = `/api/clubs${suffix}`
  if (page !== undefined && size !== undefined) {
    const sep = path.includes('?') ? '&' : '?'
    path += `${sep}page=${page}&size=${size}`
  }
  return path
}

export const fetchClubs = async (options: FetchClubsOptions = {}) => {
  const { force = false } = options
  const { page, size } = options
  const cacheKey = `page:${page ?? 'default'}:size:${size ?? 'default'}`
  const now = Date.now()

  if (!force && clubsCache && cacheKey === clubsCacheKey && now < clubsCacheExpiresAt) {
    return cloneClubs(clubsCache)
  }

  if (!force && clubsRequest && clubsRequestKey === cacheKey) {
    return cloneClubs(await clubsRequest)
  }

  clubsRequestKey = cacheKey
  clubsRequest = request<Club[]>(clubPath('', page, size))

  try {
    const clubs = await clubsRequest
    setClubsCache(cacheKey, clubs)
    return cloneClubs(clubs)
  } finally {
    clubsRequest = null
    clubsRequestKey = ''
  }
}

export const fetchAllClubs = async (force = false) => {
  const now = Date.now()
  if (!force && allClubsCache && now < allClubsCacheExpiresAt) {
    return cloneClubs(allClubsCache)
  }
  if (!force && allClubsRequest) {
    return cloneClubs(await allClubsRequest)
  }

  allClubsRequest = (async () => {
    const allClubs: Club[] = []
    for (let page = 0; ; page++) {
      const batch = await request<Club[]>(clubPath('', page, ALL_CLUBS_PAGE_SIZE))
      allClubs.push(...batch)
      if (batch.length < ALL_CLUBS_PAGE_SIZE) {
        return allClubs
      }
    }
  })()

  try {
    const allClubs = await allClubsRequest
    allClubsCache = cloneClubs(allClubs)
    allClubsCacheExpiresAt = Date.now() + CLUBS_CACHE_TTL_MS
    return cloneClubs(allClubs)
  } finally {
    allClubsRequest = null
  }
}

export const fetchClubCount = async () => {
  const response = await request<{ count: number }>(clubPath('/count'))
  return response.count
}

export const fetchClubById = (id: number | string) =>
  request<Club>(clubPath(`/${id}`))

export const updateClub = async (
  id: number | string,
  data: Partial<Club>,
) => {
  const updatedClub = await request<Club>(clubPath(`/${id}`), {
    method: 'PUT',
    body: JSON.stringify(data)})
  if (clubsCache) {
    const nextClubs = clubsCache.map((club) =>
      String(club.id) === String(updatedClub.id) ? { ...club, ...updatedClub } : club,
    )
    setClubsCache(clubsCacheKey, nextClubs)
  }
  if (allClubsCache) {
    allClubsCache = allClubsCache.map((club) =>
      String(club.id) === String(updatedClub.id) ? { ...club, ...updatedClub } : club,
    )
    allClubsCacheExpiresAt = Date.now() + CLUBS_CACHE_TTL_MS
  }
  return updatedClub
}

export const fetchClubMembers = (id: number | string) =>
  request<ClubMember[]>(clubPath(`/${id}/members`))

export const updateClubMemberRole = (
  clubId: number | string,
  oauthUserId: number | string,
  roleName: string,
) =>
  request<void>(clubPath(`/${clubId}/members/${oauthUserId}/role`), {
    method: 'PUT',
    body: JSON.stringify({ roleName }),
  })

export const applyToClub = (id: number | string) =>
  request<void>(clubPath(`/${id}/members/apply`), {
    method: 'POST',
    body: JSON.stringify({})})

export const cancelMembershipRequest = (id: number | string) =>
  request<void>(clubPath(`/${id}/members/apply`), {
    method: 'DELETE'})

export const fetchMembershipRequests = (id: number | string) =>
  request<ClubMembershipRequest[]>(
    clubPath(`/${id}/membership-requests`),
  )

export const approveMembershipRequest = (
  clubId: number | string,
  requestId: number | string,
) =>
  request<void>(
    clubPath(`/${clubId}/membership-requests/${requestId}/approve`),
    { method: 'POST', body: JSON.stringify({}) },
  )

export interface CalendarEvent {
  clubId: number
  clubName: string
  clubSlug: string | null
  category: string
  meetingSchedule: string
  scheduleNote: string | null
  location: string | null
  advisor: string | null
}

export const fetchCalendar = () =>
  request<CalendarEvent[]>(`/api/clubs/calendar`)

export const rejectMembershipRequest = (
  clubId: number | string,
  requestId: number | string,
) =>
  request<void>(clubPath(`/${clubId}/membership-requests/${requestId}`), {
    method: 'DELETE'})

export const createClub = async (data: Partial<Club>) => {
  const club = await request<Club>(clubPath(''), {
    method: 'POST',
    body: JSON.stringify(data),
  })
  invalidateClubCache()
  return club
}
