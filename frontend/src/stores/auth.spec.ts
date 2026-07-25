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

  it('dedupes concurrent calls into a single underlying request', async () => {
    const deferred = createDeferred<AuthUser>()
    fetchAuthenticatedUserMock.mockReturnValue(deferred.promise)
    const store = useAuthStore()

    const first = store.refreshUser()
    const second = store.refreshUser()

    deferred.resolve(knownUser)
    await Promise.all([first, second])

    expect(fetchAuthenticatedUserMock).toHaveBeenCalledTimes(1)
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
