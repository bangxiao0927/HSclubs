const fallbackBaseUrl = ''
const rawBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? fallbackBaseUrl).trim()
const apiBaseUrl = rawBaseUrl.replace(/\/$/, '')

const isAbsoluteUrl = (value: string) => /^https?:\/\//i.test(value)

export const buildApiUrl = (path: string) => {
  if (!path) {
    return apiBaseUrl
  }

  if (!apiBaseUrl || isAbsoluteUrl(path)) {
    return path
  }

  return path.startsWith('/') ? `${apiBaseUrl}${path}` : `${apiBaseUrl}/${path}`
}

export const getApiBaseUrl = () => apiBaseUrl
