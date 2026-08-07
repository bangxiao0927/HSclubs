import type { Club, ClubMembershipRequest } from '../types/club'
import { buildApiUrl, notifyIfUnauthorized } from './httpClient'
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const headers = new Headers(init?.headers as HeadersInit | undefined)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(url, { ...init, credentials: 'include', headers })
  if (!response.ok) {
    notifyIfUnauthorized(response)
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }
  const raw = await response.text()
  if (!raw) return undefined as T
  return JSON.parse(raw) as T
}


const withCredentials = (init?: RequestInit): RequestInit => ({
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    ...(init?.headers || {})},
  ...init})

const readErrorMessage = async (response: Response) => {
  const text = await response.text()
  return text || `Request failed with status ${response.status}`
}

export const updateGraduationYear = async (graduationYear: number): Promise<void> => {
  const response = await fetch(
    buildApiUrl('/api/users/me/graduation-year'),
    withCredentials({
      method: 'PATCH',
      body: JSON.stringify({ graduationYear })}),
  )

  if (!response.ok) {
    notifyIfUnauthorized(response)
    throw new Error(await readErrorMessage(response))
  }
}

export const fetchMyClubs = () => request<Club[]>('/api/users/me/clubs')

export const fetchMyMembershipRequests = () =>
  request<ClubMembershipRequest[]>('/api/users/me/membership-requests')

export interface UserSearchResult {
  id: number | null
  email: string
  displayName: string
  avatarUrl: string | null
}

export const searchUsers = (query: string, limit = 10) =>
  request<UserSearchResult[]>(`/api/users/search?q=${encodeURIComponent(query)}&limit=${limit}`)

export const assignPresident = (clubId: number | string, userId: number) =>
  request<void>(`/api/clubs/${clubId}/presidents/${userId}`, { method: 'POST' })

export const removePresident = (clubId: number | string, userId: number) =>
  request<void>(`/api/clubs/${clubId}/presidents/${userId}`, { method: 'DELETE' })
