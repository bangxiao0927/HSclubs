import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthProvider, AuthUser } from '../types/auth'

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../utils/authRedirect', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../utils/authRedirect')>()
  return {
    ...actual,
    savePendingAuthRedirect: vi.fn(),
  }
})

import { fetchAuthenticatedUser, fetchAuthProviders, logout as apiLogout } from '../services/authService'
import { reportUnauthorized } from '../services/httpClient'
import { savePendingAuthRedirect } from '../utils/authRedirect'
import { useAuthStore } from './auth'

const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)
const fetchAuthProvidersMock = vi.mocked(fetchAuthProviders)
const apiLogoutMock = vi.mocked(apiLogout)
const savePendingAuthRedirectMock = vi.mocked(savePendingAuthRedirect)

const knownUser: AuthUser = {
  id: 'user-1',
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  avatarUrl: '',
  provider: 'google',
  isOwner: false,
  graduationYear: 2026,
  acceptedTerms: true,
}

// A deferred promise gives the test control over exactly when the mocked
// fetch resolves, so concurrent refreshUser() calls can be started before
// either one has settled.
const createDeferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  setActivePinia(createPinia())
  fetchAuthenticatedUserMock.mockReset()
})

describe('refreshUser', () => {
  it('populates currentUser and marks the session as checked on success', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(knownUser)
    const store = useAuthStore()

    await store.refreshUser()

    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userLoading).toBe(false)
    expect(store.userError).toBeNull()
    expect(store.hasCheckedSession).toBe(true)
  })

  it('always issues a fresh request, even for back-to-back calls (no dedupe: post-mutation callers must see current server state)', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(knownUser)
    const store = useAuthStore()

    const first = store.refreshUser()
    const second = store.refreshUser()
    await Promise.all([first, second])

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(2)
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userLoading).toBe(false)
    expect(store.hasCheckedSession).toBe(true)
  })

  it('does not cache a poisoned promise after a rejected request', async () => {
    fetchAuthenticatedUserMock.mockRejectedValueOnce(new Error('network down'))
    const store = useAuthStore()

    await store.refreshUser()

    expect(store.currentUser).toBeNull()
    expect(store.userError).toBe('network down')
    expect(store.hasCheckedSession).toBe(true)

    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    await store.refreshUser()

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(2)
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userError).toBeNull()
  })

  // fetchAuthenticatedUser returns null for a real 401 and only throws for a network error or
  // a 5xx. Collapsing both into "signed out" meant one blip on cold load kicked a student with
  // a perfectly valid session off whatever page they were on.
  it('keeps the signed-in user when the session check fails for a transient reason', async () => {
    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    const store = useAuthStore()
    await store.refreshUser()

    fetchAuthenticatedUserMock.mockRejectedValueOnce(new Error('Failed to fetch'))
    await store.refreshUser()

    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userError).toBe('Failed to fetch')
  })

  it('still clears the user when the server actually reports no session', async () => {
    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    const store = useAuthStore()
    await store.refreshUser()

    fetchAuthenticatedUserMock.mockResolvedValueOnce(null)
    await store.refreshUser()

    expect(store.currentUser).toBeNull()
  })
})

describe('logout', () => {
  it('clears the local session even when the server call fails, without rejecting', async () => {
    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    const store = useAuthStore()
    await store.refreshUser()

    apiLogoutMock.mockRejectedValueOnce(new Error('401 Unauthorized'))
    await expect(store.logout()).resolves.toBeUndefined()

    expect(store.currentUser).toBeNull()
  })
})

describe('unauthorized responses from other endpoints', () => {
  // Any data endpoint answering 401 means this session is gone. Without this the store went on
  // reporting the user as signed in, so the router guards kept letting them into pages where
  // every request then failed with a raw server error body.
  it('clears the signed-in user', async () => {
    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    const store = useAuthStore()
    await store.refreshUser()

    reportUnauthorized()

    expect(store.currentUser).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })
})

describe('ensureSessionChecked', () => {
  it('dedupes two concurrent session-style checks into a single underlying request, both observing the same result', async () => {
    const deferred = createDeferred<AuthUser>()
    fetchAuthenticatedUserMock.mockReturnValue(deferred.promise)
    const store = useAuthStore()

    const first = store.ensureSessionChecked()
    const second = store.ensureSessionChecked()

    deferred.resolve(knownUser)
    await Promise.all([first, second])

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(1)
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userLoading).toBe(false)
    expect(store.hasCheckedSession).toBe(true)
  })

  it('does not cache a poisoned promise after a rejected request: the next session check genuinely refetches', async () => {
    fetchAuthenticatedUserMock.mockRejectedValueOnce(new Error('network down'))
    const store = useAuthStore()

    await store.ensureSessionChecked()

    expect(store.currentUser).toBeNull()
    expect(store.userError).toBe('network down')
    expect(store.hasCheckedSession).toBe(true)

    fetchAuthenticatedUserMock.mockResolvedValueOnce(knownUser)
    await store.ensureSessionChecked()

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(2)
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.userError).toBeNull()
  })

  it('a cold load issues exactly ONE /api/auth/me request even though bootstrap() and a guard-style session check both run concurrently', async () => {
    fetchAuthenticatedUserMock.mockResolvedValue(knownUser)
    const store = useAuthStore()

    // Mirrors main.ts calling bootstrap() and the router guard's
    // `if (!hasCheckedSession) await authStore.ensureSessionChecked()`
    // firing before either has resolved on a cold load.
    await Promise.all([store.bootstrap(), store.ensureSessionChecked()])

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(1)
    expect(store.currentUser?.id).toBe('user-1')
    expect(store.hasCheckedSession).toBe(true)
  })
})

describe('refreshUser vs. ensureSessionChecked concurrency (Finding-1 regression)', () => {
  it('regression: a post-mutation refreshUser() (e.g. right after the accept-terms POST) is not handed a stale response from a concurrently in-flight session check', async () => {
    const staleUser = { ...knownUser, acceptedTerms: false }
    const freshUser = { ...knownUser, acceptedTerms: true }
    const sessionCheckDeferred = createDeferred<AuthUser>()
    const postMutationDeferred = createDeferred<AuthUser>()

    fetchAuthenticatedUserMock
      .mockReturnValueOnce(sessionCheckDeferred.promise)
      .mockReturnValueOnce(postMutationDeferred.promise)

    const store = useAuthStore()

    // A router-guard-style session check is already in flight when
    // AcceptTermsView's POST finishes and it calls refreshUser() to see
    // whether the user is now allowed past the accept-terms guard.
    const sessionCheck = store.ensureSessionChecked()
    const postMutationRefresh = store.refreshUser()

    // Both underlying requests are in flight: refreshUser() must have
    // issued its OWN request rather than being handed the session check's.
    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(2)

    sessionCheckDeferred.resolve(staleUser)
    postMutationDeferred.resolve(freshUser)
    await Promise.all([sessionCheck, postMutationRefresh])

    // The post-mutation caller's own fresh response (acceptedTerms: true)
    // must be what the store ends up reflecting, not the pre-mutation
    // response the session check happened to have in flight.
    expect(store.currentUser?.acceptedTerms).toBe(true)
  })
})

describe('beginLogin', () => {
  const googleProvider: AuthProvider = {
    id: 'google',
    name: 'Google',
    authorizationUrl: '/api/auth/authorize/google',
  }

  let originalLocation: Location

  beforeEach(() => {
    originalLocation = window.location
    // jsdom's real navigation throws "Not implemented"; replace `location`
    // with a plain writable stub so `beginLogin` can assign `href` and the
    // test can read back exactly what it navigated to.
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { href: '' },
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    })
  })

  it('navigates to the authorization URL with an encoded redirect param, and still saves the pending redirect', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    const store = useAuthStore()
    await store.ensureProvidersLoaded()

    store.beginLogin('google', '/clubs/9?tab=events')

    expect(window.location.href).toBe(
      'http://localhost:8080/api/auth/authorize/google?redirect=%2Fclubs%2F9%3Ftab%3Devents',
    )
    expect(savePendingAuthRedirectMock).toHaveBeenCalledWith('/clubs/9?tab=events')
  })

  it('navigates to the bare authorization URL when no redirect target is supplied', async () => {
    fetchAuthProvidersMock.mockResolvedValue([googleProvider])
    const store = useAuthStore()
    await store.ensureProvidersLoaded()

    store.beginLogin('google', null)

    expect(window.location.href).toBe('http://localhost:8080/api/auth/authorize/google')
    expect(savePendingAuthRedirectMock).toHaveBeenCalledWith(null)
  })
})
