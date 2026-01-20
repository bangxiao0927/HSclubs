import { buildApiUrl } from './httpClient'

const withCredentials = (init?: RequestInit): RequestInit => ({
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    ...(init?.headers || {}),
  },
  ...init,
})

const readErrorMessage = async (response: Response) => {
  const text = await response.text()
  return text || `Request failed with status ${response.status}`
}

export const updateGraduationYear = async (graduationYear: number): Promise<void> => {
  const response = await fetch(
    buildApiUrl('/api/users/me/graduation-year'),
    withCredentials({
      method: 'PATCH',
      body: JSON.stringify({ graduationYear }),
    }),
  )

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }
}
