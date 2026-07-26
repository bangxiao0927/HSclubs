import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuthUser } from '../types/auth'

vi.mock('../services/authService', () => ({
  fetchAuthProviders: vi.fn(),
  fetchAuthenticatedUser: vi.fn(),
  logout: vi.fn(),
}))

import { fetchAuthenticatedUser } from '../services/authService'
import { useAuthStore } from './auth'

const fetchAuthenticatedUserMock = vi.mocked(fetchAuthenticatedUser)

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
