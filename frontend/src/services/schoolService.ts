import { buildApiUrl } from './httpClient'

export interface SchoolSummary {
  id: number
  slug: string
  schoolName: string
  shortName: string | null
  logoUrl: string | null
  bannerUrl: string | null
  primaryColor: string | null
  status: string
}

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

export const fetchSchools = () => request<SchoolSummary[]>('/api/schools')

export const fetchSchoolBySlug = (slug: string) =>
  request<SchoolSummary>(`/api/schools/${slug}`)
