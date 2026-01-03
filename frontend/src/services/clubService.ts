import type { Club, ClubMember } from '../types/club'
import { buildApiUrl } from './httpClient'

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

export const fetchClubs = () => request<Club[]>('/api/clubs')

export const fetchClubById = (id: number | string) => request<Club>(`/api/clubs/${id}`)

export const updateClub = (id: number | string, data: Partial<Club>) =>
  request<Club>(`/api/clubs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })

export const fetchClubMembers = (id: number | string) => request<ClubMember[]>(`/api/clubs/${id}/members`)

export const applyToClub = (id: number | string) =>
  request<void>(`/api/clubs/${id}/members/apply`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
