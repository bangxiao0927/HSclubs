import { buildApiUrl } from './httpClient'

export interface SchoolFormData {
  slug: string
  schoolName: string
  shortName?: string
  logoUrl?: string
  bannerUrl?: string
  primaryColor?: string
  schoolDomain?: string
  timezone?: string
  status?: string
}

export interface SchoolAdmin extends SchoolFormData {
  id: number
  createdAt: string
  updatedAt: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const headers = new Headers(init?.headers as HeadersInit | undefined)
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(url, {
    ...init,
    credentials: 'include',
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

export const fetchPlatformSchools = () => request<SchoolAdmin[]>('/api/platform/schools')

export const createSchool = (data: SchoolFormData) =>
  request<SchoolAdmin>('/api/platform/schools', {
    method: 'POST',
    body: JSON.stringify(data),
  })

export const updateSchool = (slug: string, data: Partial<SchoolFormData>) =>
  request<SchoolAdmin>(`/api/platform/schools/${slug}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
