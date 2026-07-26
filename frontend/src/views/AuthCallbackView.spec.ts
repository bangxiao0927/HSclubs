import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: vi.fn(),
  useRouter: vi.fn(),
}))

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import { useRoute, useRouter } from 'vue-router'

import { fetchAuthenticatedUser } from '../services/authService'
import type { AuthUser } from '../types/auth'
import { consumePendingAuthRedirect, savePendingAuthRedirect } from '../utils/authRedirect'
import AuthCallbackView from './AuthCallbackView.vue'

const useRouteMock = vi.mocked(useRoute)
const useRouterMock = vi.mocked(useRouter)
const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)

// A fully-onboarded user by default; individual tests override the fields
// that make a real difference to the post-auth routing decision.
const buildUser = (overrides: Partial<AuthUser> = {}): AuthUser => ({
  id: 'user-1',
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  avatarUrl: '',
  provider: 'google',
  isOwner: false,
  graduationYear: 2026,
  acceptedTerms: true,
  ...overrides,
})

let replaceMock: ReturnType<typeof vi.fn>

// Mounts the view with a given `route.query`, waiting for the async
// onMounted handler (and the store call it awaits) to settle.
const mountWithQuery = async (query: Record<string, string>) => {
  useRouteMock.mockReturnValue({ query } as ReturnType<typeof useRoute>)
  const wrapper = mount(AuthCallbackView)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
  fetchAuthenticatedUserMock.mockReset()
  replaceMock = vi.fn()
  useRouterMock.mockReturnValue({ replace: replaceMock } as unknown as ReturnType<typeof useRouter>)
  useRouteMock.mockReturnValue({ query: {} } as ReturnType<typeof useRoute>)
})

describe('AuthCallbackView', () => {
  it('sends a fully-onboarded user straight to the requested redirect target', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())

    await mountWithQuery({ redirect: '/clubs/3' })

    expect(replaceMock).toHaveBeenCalledWith('/clubs/3')
  })

  it('sends a brand-new user (terms not yet accepted) to accept-terms, carrying the target along', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(
      buildUser({ acceptedTerms: false, graduationYear: null }),
    )

    await mountWithQuery({ redirect: '/clubs/3' })

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/accept-terms',
      query: { redirect: '/clubs/3' },
    })
  })

  it('sends a user who accepted terms but has no graduation year to onboarding, carrying the target along', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(
      buildUser({ acceptedTerms: true, graduationYear: null }),
    )

    await mountWithQuery({ redirect: '/clubs/3' })

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/onboarding',
      query: { redirect: '/clubs/3' },
    })
  })

  it('falls back to a pending redirect saved in sessionStorage when the URL carries none, and consumes it', async () => {
    savePendingAuthRedirect('/clubs/9')
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())

    await mountWithQuery({})

    expect(replaceMock).toHaveBeenCalledWith('/clubs/9')
    // The pending redirect must not be left behind for a later, unrelated login.
    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('prevents a stale pending redirect from hijacking a later, unrelated login when the server-provided redirect wins', async () => {
    // The server-provided `?redirect=` takes precedence over the stored
    // fallback (regression check for that precedence), but the stored
    // value must still be cleared as a side effect -- otherwise it would
    // survive to hijack a later login attempt that has no server-side
    // target of its own (e.g. a bookmarked/direct authorization URL that
    // never went through beginLogin()).
    savePendingAuthRedirect('/clubs/9')
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())

    await mountWithQuery({ redirect: '/clubs/3' })

    expect(replaceMock).toHaveBeenCalledWith('/clubs/3')
    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('lands a fully-onboarded user with no redirect target at all on the default profile page', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(buildUser())

    await mountWithQuery({})

    expect(replaceMock).toHaveBeenCalledWith('/profile')
  })

  it.each(['https://evil.com', '//evil.com'])(
    'never lets an attacker-controlled redirect target (%s) reach router.replace, and falls back to the default',
    async (maliciousTarget) => {
      fetchAuthenticatedUserMock.mockResolvedValue(buildUser())

      await mountWithQuery({ redirect: maliciousTarget })

      expect(replaceMock).not.toHaveBeenCalledWith(maliciousTarget)
      expect(replaceMock).toHaveBeenCalledWith('/profile')
    },
  )

  it('skips fetching the user entirely when the backend already reports an oauth2 failure, and goes to /auth', async () => {
    await mountWithQuery({ error: 'oauth2_login_failed' })

    expect(fetchAuthenticatedUserMock).not.toHaveBeenCalled()
    expect(replaceMock).toHaveBeenCalledWith({
      path: '/auth',
      query: { error: 'oauth2_login_failed' },
    })
  })

  it('clears a pending redirect on a backend-reported failure so it cannot hijack the next login attempt', async () => {
    savePendingAuthRedirect('/clubs/9')

    await mountWithQuery({ error: 'oauth2_login_failed' })

    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('clears a pending redirect when refreshUser() comes back with no user, so it cannot hijack the next login attempt', async () => {
    savePendingAuthRedirect('/clubs/9')
    fetchAuthenticatedUserMock.mockResolvedValue(null)

    await mountWithQuery({})

    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('forwards a valid remembered target alongside the error on a backend-reported failure, so a retry can resume', async () => {
    await mountWithQuery({ error: 'oauth2_login_failed', redirect: '/clubs/9' })

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/auth',
      query: { error: 'oauth2_login_failed', redirect: '/clubs/9' },
    })
  })

  it.each(['https://evil.com', '//evil.com', '/\\evil.com'])(
    'drops a hostile remembered target (%s) on a backend-reported failure but still forwards the error',
    async (maliciousTarget) => {
      await mountWithQuery({ error: 'oauth2_login_failed', redirect: maliciousTarget })

      expect(replaceMock).toHaveBeenCalledWith({
        path: '/auth',
        query: { error: 'oauth2_login_failed' },
      })
    },
  )

  it('forwards only the error, with no redirect key at all, when a backend-reported failure carries no remembered target', async () => {
    await mountWithQuery({ error: 'oauth2_login_failed' })

    expect(replaceMock).toHaveBeenCalledWith({
      path: '/auth',
      query: { error: 'oauth2_login_failed' },
    })
    const call = replaceMock.mock.calls[0]?.[0] as { query: Record<string, string> }
    expect('redirect' in call.query).toBe(false)
  })
})
