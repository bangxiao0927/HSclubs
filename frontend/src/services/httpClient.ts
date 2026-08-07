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

// A 401 from any data endpoint means the session died while the tab was open. Without a shared
// place to say so, each service turned it into a plain Error and the auth store went on
// reporting the user as signed in, so the router guards kept letting them into pages where
// every request then failed the same way. Services report it here; the auth store is what
// listens and clears itself (frontend/src/stores/auth.ts).
type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

export const setUnauthorizedHandler = (handler: UnauthorizedHandler | null) => {
  unauthorizedHandler = handler
}

export const reportUnauthorized = () => {
  unauthorizedHandler?.()
}

/**
 * Call for every non-ok API response. Returns the response so callers can keep their own
 * error handling unchanged.
 */
export const notifyIfUnauthorized = (response: Response) => {
  if (response.status === 401) {
    reportUnauthorized()
  }
  return response
}
