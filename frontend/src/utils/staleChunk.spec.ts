import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearStaleChunkRecovery,
  isStaleChunkError,
  recoverFromStaleChunk,
} from './staleChunk'

beforeEach(() => {
  window.sessionStorage.clear()
})

describe('isStaleChunkError', () => {
  it.each([
    'Failed to fetch dynamically imported module: https://hsclubs.net/assets/OnboardingView-abc123.js',
    'error loading dynamically imported module',
    'Importing a module script failed.',
    'Loading chunk 42 failed.',
  ])('recognizes the browser wording "%s"', (message) => {
    expect(isStaleChunkError(new Error(message))).toBe(true)
  })

  it('ignores unrelated navigation failures so they still surface as real errors', () => {
    expect(isStaleChunkError(new Error('Network request failed'))).toBe(false)
    expect(isStaleChunkError(undefined)).toBe(false)
    expect(isStaleChunkError(null)).toBe(false)
  })
})

describe('recoverFromStaleChunk', () => {
  it('reloads at the requested target so the browser refetches the current chunk manifest', () => {
    const reload = vi.fn()

    expect(recoverFromStaleChunk('/onboarding?redirect=/profile', reload)).toBe(true)
    expect(reload).toHaveBeenCalledWith('/onboarding?redirect=/profile')
  })

  it('reloads a given target at most once, so a chunk that is genuinely gone cannot loop forever', () => {
    const reload = vi.fn()

    recoverFromStaleChunk('/onboarding', reload)
    const second = recoverFromStaleChunk('/onboarding', reload)

    expect(second).toBe(false)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('still recovers a different target while one target is already marked', () => {
    const reload = vi.fn()

    recoverFromStaleChunk('/onboarding', reload)
    expect(recoverFromStaleChunk('/profile', reload)).toBe(true)
  })

  it('retries the same target again once a later navigation has succeeded', () => {
    const reload = vi.fn()

    recoverFromStaleChunk('/onboarding', reload)
    clearStaleChunkRecovery()

    expect(recoverFromStaleChunk('/onboarding', reload)).toBe(true)
    expect(reload).toHaveBeenCalledTimes(2)
  })

  it('does not reload when the marker cannot be stored, since the loop could not be broken', () => {
    const reload = vi.fn()
    // Node 25 exposes its own global Storage, which is not jsdom's window Storage.
    // Spy on the prototype used by the browser object the implementation actually calls.
    const storagePrototype = Object.getPrototypeOf(window.sessionStorage) as Storage
    const setItem = vi.spyOn(storagePrototype, 'setItem').mockImplementation(() => {
      throw new Error('storage disabled')
    })

    expect(recoverFromStaleChunk('/onboarding', reload)).toBe(false)
    expect(reload).not.toHaveBeenCalled()

    setItem.mockRestore()
  })
})
