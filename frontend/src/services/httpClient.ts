const fallbackBaseUrl = ''
const rawBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? fallbackBaseUrl).trim()
const apiBaseUrl = rawBaseUrl.replace(/\/$/, '')
const baseIsAbsolute = apiBaseUrl.length > 0 && /^https?:\/\//i.test(apiBaseUrl)

const isAbsoluteUrl = (value: string) => /^https?:\/\//i.test(value)

export const buildApiUrl = (path: string) => {
  if (!path) {
    return apiBaseUrl
  }

  if (!apiBaseUrl || isAbsoluteUrl(path)) {
    return path
  }

  if (!baseIsAbsolute && path.startsWith('/')) {
    return path
  }

  if (baseIsAbsolute) {
    const normalizedPath = path.startsWith('/') ? path : `/${path}`
    try {
      return new URL(normalizedPath, apiBaseUrl).toString()
    } catch {
      return `${apiBaseUrl}${normalizedPath}`
    }
  }

  return path.startsWith('/') ? `${apiBaseUrl}${path}` : `${apiBaseUrl}/${path}`
}

export const getApiBaseUrl = () => apiBaseUrl
