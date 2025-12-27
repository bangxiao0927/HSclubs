import type { Club } from '../types/club'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = apiBaseUrl ? `${apiBaseUrl}${path}` : path
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
