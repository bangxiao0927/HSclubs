import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import type { AuthUser } from '../types/auth'
import { fetchAuthenticatedUser } from '../services/authService'
import router from './index'

const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)

// A fully-onboarded user: accepted the terms and has a graduation year on
// file, so none of the post-auth steps (accept-terms, onboarding) apply.
const onboardedUser: AuthUser = {
  id: 'user-1',
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  avatarUrl: '',
  provider: 'google',
  isOwner: false,
  graduationYear: 2026,
  acceptedTerms: true,
}

// The catch-all 404 route: it has no `requiresAuth` meta and is in the
// guard's `termsBypassRouteNames` allowlist, so navigating here never
// triggers a redirect for ANY auth/terms/onboarding combination. That makes
// it a safe "neutral" waypoint to move the router away from wherever the
// previous test left `currentRoute`, without that move itself being
// redirected (which would make its `refreshUser()` call reflect the wrong
// session for the test that follows).
const NEUTRAL_RESET_PATH = '/__router-spec-reset__'

/**
 * Establishes a fresh Pinia store, wires the mocked `fetchAuthenticatedUser`
 * to resolve as `user`, and performs the one navigation that actually
 * triggers the guard's `refreshUser()` call (since `hasCheckedSession` stays
 * true forever after). Every subsequent `router.push` in the test reuses
 * that already-resolved session.
 */
const primeSession = async (user: AuthUser | null) => {
  setActivePinia(createPinia())
  fetchAuthenticatedUserMock.mockReset()
  fetchAuthenticatedUserMock.mockResolvedValue(user)
  await router.push(NEUTRAL_RESET_PATH)
  await router.isReady()
}

beforeEach(() => {
  fetchAuthenticatedUserMock.mockReset()
})

describe('authentication gate', () => {
  it('redirects an unauthenticated user requesting a protected route to auth-choice, preserving the original path', async () => {
    await primeSession(null)

    await router.push('/profile')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('auth-choice')
    expect(router.currentRoute.value.query.intent).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
  })

  it('still renders the sign-in page for an unauthenticated visitor (no redirect)', async () => {
    await primeSession(null)

    await router.push('/auth')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('auth-choice')
    expect(router.currentRoute.value.fullPath).toBe('/auth')
  })

  it('leaves a public route untouched for an unauthenticated visitor', async () => {
    await primeSession(null)

    await router.push('/search')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('club-search')
  })

  it('leaves the club media route reachable for an unauthenticated visitor', async () => {
    await primeSession(null)

    await router.push('/clubs/3/media')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('club-detail')
  })
})

describe('legacy club media route redirect', () => {
  it('redirects /clubs/:id/media to /clubs/:id with the #media hash', async () => {
    await primeSession(null)

    await router.push('/clubs/3/media')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/clubs/3')
    expect(router.currentRoute.value.hash).toBe('#media')
  })

  it('preserves the query string from the legacy media route', async () => {
    await primeSession(null)

    await router.push('/clubs/3/media?page=2&size=6')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/clubs/3')
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(router.currentRoute.value.query.size).toBe('6')
    expect(router.currentRoute.value.hash).toBe('#media')
  })
})

describe('already-authenticated user landing on the sign-in page', () => {
  it('sends a fully-onboarded user to the default landing path', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('forwards a fully-onboarded user to the requested redirect target', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth?intent=login&redirect=/clubs/3')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('club-detail')
    expect(router.currentRoute.value.path).toBe('/clubs/3')
  })

  it('does not follow an off-site redirect target, falling back to the default landing path', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth?redirect=https://evil.com')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('does not follow a backslash redirect target that would resolve cross-origin, falling back to the default landing path', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth?redirect=/\\evil.com')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('does not follow a percent-encoded backslash redirect target, falling back to the default landing path', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth?redirect=%2F%5Cevil.com')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('regression: preserves a query string embedded in the redirect target when forwarding a fully-onboarded user', async () => {
    await primeSession(onboardedUser)

    // `redirect` is set from a full path that itself carries a query string
    // (e.g. `to.fullPath` for a bookmarked, query-bearing deep link).
    await router.push({ name: 'auth-choice', query: { redirect: '/clubs/3?ref=email' } })
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('club-detail')
    expect(router.currentRoute.value.fullPath).toBe('/clubs/3?ref=email')
    expect(router.currentRoute.value.query.ref).toBe('email')
  })

  it('preserves multiple query params and a hash when forwarding a fully-onboarded user to the redirect target', async () => {
    await primeSession(onboardedUser)

    await router.push({
      name: 'auth-choice',
      query: { redirect: '/search?q=chess&sort=name#results' },
    })
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('club-search')
    expect(router.currentRoute.value.fullPath).toBe('/search?q=chess&sort=name#results')
    expect(router.currentRoute.value.query.q).toBe('chess')
    expect(router.currentRoute.value.query.sort).toBe('name')
    expect(router.currentRoute.value.hash).toBe('#results')
  })

  it('preserves the nested redirect query when forwarding a not-yet-onboarded user to onboarding', async () => {
    await primeSession({ ...onboardedUser, graduationYear: null })

    await router.push('/auth?redirect=/clubs/3')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('onboarding')
    expect(router.currentRoute.value.query.redirect).toBe('/clubs/3')
  })

  it('replaces the /auth history entry instead of pushing a new one, so Back does not bounce between /auth and the destination', async () => {
    await primeSession(onboardedUser)
    await router.push('/search')
    await router.isReady()
    const historyLengthBeforeAuth = window.history.length

    await router.push('/auth')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/profile')

    // A `replace` navigation overwrites the current history entry rather
    // than appending one, so the entry count must not have grown across the
    // /auth -> /profile guard redirect.
    expect(window.history.length).toBe(historyLengthBeforeAuth)
  })

  it('sends a user who has not accepted the terms to accept-terms instead', async () => {
    await primeSession({ ...onboardedUser, acceptedTerms: false })

    await router.push('/auth')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('accept-terms')
  })

  it('sends a user with no graduation year to onboarding instead', async () => {
    await primeSession({ ...onboardedUser, graduationYear: null })

    await router.push('/auth')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('onboarding')
  })

  it('does not hijack the OAuth callback route (only auth-choice is redirected onward)', async () => {
    await primeSession(onboardedUser)

    await router.push('/auth/callback')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('auth-callback')
  })
})

describe('terms enforcement', () => {
  it('redirects a user who has not accepted the terms to accept-terms, preserving the requested route', async () => {
    await primeSession({ ...onboardedUser, acceptedTerms: false })

    await router.push('/profile')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('accept-terms')
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
  })

  it('still allows a user who has not accepted the terms to reach the terms and privacy pages', async () => {
    await primeSession({ ...onboardedUser, acceptedTerms: false })

    await router.push('/terms')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('terms')

    await router.push('/privacy')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('privacy')
  })

  it('regression: after accepting the terms, a user with no graduation year is forwarded to onboarding, not the original redirect target', async () => {
    await primeSession({ ...onboardedUser, acceptedTerms: true, graduationYear: null })

    await router.push('/accept-terms?redirect=/clubs/3')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('onboarding')
    expect(router.currentRoute.value.query.redirect).toBe('/clubs/3')
  })

  it('does not loop when the redirect target is accept-terms itself, terminating on the default landing path', async () => {
    await primeSession(onboardedUser)

    await router.push('/accept-terms?redirect=/accept-terms')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/profile')
  })
})

describe('owner gate', () => {
  it('redirects an authenticated non-owner away from an owner-only route, back to home', async () => {
    await primeSession({ ...onboardedUser, isOwner: false })

    await router.push('/admin')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('home')
  })
})

describe('removed multi-school invitation flow', () => {
  it('regression: /accept-invitation is no longer a registered route, falling through to the 404 page', async () => {
    await primeSession(onboardedUser)

    await router.push('/accept-invitation?token=abc')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('not-found')
  })
})
