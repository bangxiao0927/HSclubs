const PENDING_AUTH_REDIRECT_KEY = 'hsclubs.pendingAuthRedirect'

export const normalizeAuthRedirect = (value: unknown) => {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
    return null
  }
  return trimmed
}

export const savePendingAuthRedirect = (value: unknown) => {
  const redirect = normalizeAuthRedirect(value)
  try {
    if (redirect) {
      sessionStorage.setItem(PENDING_AUTH_REDIRECT_KEY, redirect)
    } else {
      sessionStorage.removeItem(PENDING_AUTH_REDIRECT_KEY)
    }
  } catch {
    // Ignore storage failures; the login flow can still fall back safely.
  }
}

export const consumePendingAuthRedirect = () => {
  try {
    const redirect = normalizeAuthRedirect(sessionStorage.getItem(PENDING_AUTH_REDIRECT_KEY))
    sessionStorage.removeItem(PENDING_AUTH_REDIRECT_KEY)
    return redirect
  } catch {
    return null
  }
}
