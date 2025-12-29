import type { Club } from '../types/club'
import { buildApiUrl } from './httpClient'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
    },
    ...init,
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  return (await response.json()) as T
}

export const fetchClubs = () => request<Club[]>('/api/clubs')

export const fetchClubById = (id: number | string) => request<Club>(`/api/clubs/${id}`)
