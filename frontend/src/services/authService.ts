import type { AuthProvider, AuthUser } from '../types/auth'
import { buildApiUrl } from './httpClient'

const withCredentials = (init?: RequestInit): RequestInit => ({
  credentials: 'include',
  ...init})

const readErrorMessage = async (response: Response) => {
  const text = await response.text()
  return text || `Request failed with status ${response.status}`
}

export const fetchAuthProviders = async (): Promise<AuthProvider[]> => {
  const response = await fetch(buildApiUrl('/api/auth/providers'), withCredentials())

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }

  return (await response.json()) as AuthProvider[]
}

export const fetchAuthenticatedUser = async (): Promise<AuthUser | null> => {
  const response = await fetch(buildApiUrl('/api/auth/me'), withCredentials())

  if (response.status === 401) {
    return null
  }

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }

  return (await response.json()) as AuthUser
}

export const logout = async (): Promise<void> => {
  const response = await fetch(
    buildApiUrl('/api/auth/logout'),
    withCredentials({
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'}}),
  )

  if (!response.ok && response.status !== 204) {
    throw new Error(await readErrorMessage(response))
  }
}
