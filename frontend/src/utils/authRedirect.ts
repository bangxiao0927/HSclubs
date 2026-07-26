import type { AuthUser } from '../types/auth'

const PENDING_AUTH_REDIRECT_KEY = 'hsclubs.pendingAuthRedirect'

// Where a fully-onboarded user lands after auth if no other target applies.
export const DEFAULT_POST_AUTH_PATH = '/profile'

// Steps in the post-auth flow that must never be used as a "redirect back to"
// target, since landing back on them would create a redirect loop.
const NON_TARGETABLE_AUTH_STEPS = new Set(['/accept-terms', '/onboarding'])

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

/**
 * Normalizes a candidate redirect target and falls back to `fallback` when the
 * target is missing or points at one of the post-auth flow's own steps
 * (e.g. `/onboarding` or `/accept-terms?foo=1`), which would otherwise send
 * the user right back into the step they just left.
 */
export const sanitizeAuthRedirectTarget = (
  value: unknown,
  fallback: string = DEFAULT_POST_AUTH_PATH,
) => {
  const normalized = normalizeAuthRedirect(value)
  if (!normalized) {
    return fallback
  }
  const path = normalized.split(/[?#]/, 1)[0] ?? normalized
  const comparablePath =
    path === '/' ? path : path.toLowerCase().replace(/\/$/, '')
  return NON_TARGETABLE_AUTH_STEPS.has(comparablePath) ? fallback : normalized
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

export type PostAuthUser = Pick<AuthUser, 'acceptedTerms' | 'graduationYear'>

export type PostAuthRoute =
  | { path: '/accept-terms' | '/onboarding'; query: { redirect: string } }
  | string

/**
 * The single source of truth for "where does this user go after auth?".
 *
 * Ordering:
 *   1. no user               -> null (caller handles the unauthenticated case)
 *   2. terms not accepted    -> /accept-terms, carrying the sanitized target
 *   3. no graduation year    -> /onboarding, carrying the sanitized target
 *   4. otherwise             -> the sanitized target itself
 */
export const resolvePostAuthRoute = (
  user: PostAuthUser | null | undefined,
  rawTarget: unknown,
): PostAuthRoute | null => {
  if (!user) {
    return null
  }

  const target = sanitizeAuthRedirectTarget(rawTarget)

  if (user.acceptedTerms === false) {
    return { path: '/accept-terms', query: { redirect: target } }
  }

  if (user.graduationYear == null) {
    return { path: '/onboarding', query: { redirect: target } }
  }

  return target
}
