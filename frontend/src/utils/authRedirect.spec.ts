import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearPendingAuthRedirect,
  consumePendingAuthRedirect,
  normalizeAuthRedirect,
  resolvePostAuthRoute,
  sanitizeAuthRedirectTarget,
  savePendingAuthRedirect,
} from './authRedirect'

describe('normalizeAuthRedirect', () => {
  it('accepts a relative path', () => {
    expect(normalizeAuthRedirect('/foo')).toBe('/foo')
  })

  it('rejects absolute URLs', () => {
    expect(normalizeAuthRedirect('https://evil.com')).toBeNull()
  })

  it('rejects protocol-relative URLs', () => {
    expect(normalizeAuthRedirect('//evil.com')).toBeNull()
  })

  it('rejects non-string values', () => {
    expect(normalizeAuthRedirect(null)).toBeNull()
    expect(normalizeAuthRedirect(undefined)).toBeNull()
    expect(normalizeAuthRedirect(42)).toBeNull()
  })

  it('rejects empty or whitespace-only strings', () => {
    expect(normalizeAuthRedirect('')).toBeNull()
    expect(normalizeAuthRedirect('   ')).toBeNull()
  })

  // A backslash is a path separator to a browser's URL parser, not a literal
  // character: `new URL('/\\evil.com', 'http://localhost/')` resolves to
  // `http://evil.com/`, the same cross-origin escape the backend's
  // PostLoginRedirectResolver.isSafeInAppTarget already rejects.
  it('rejects a leading-slash-then-backslash target that resolves cross-origin', () => {
    expect(normalizeAuthRedirect('/\\evil.com')).toBeNull()
  })

  it('rejects a slash-backslash-slash target that resolves cross-origin', () => {
    expect(normalizeAuthRedirect('/\\/evil.com')).toBeNull()
  })

  it('rejects a backslash appearing mid-path, not just at the start', () => {
    expect(normalizeAuthRedirect('/clubs/3\\evil.com')).toBeNull()
  })

  it('still rejects a protocol-relative URL', () => {
    expect(normalizeAuthRedirect('//evil.com')).toBeNull()
  })

  it('still rejects an absolute cross-origin URL', () => {
    expect(normalizeAuthRedirect('https://evil.com')).toBeNull()
  })

  it('rejects a raw newline smuggled into an otherwise valid-looking target', () => {
    expect(normalizeAuthRedirect('/clubs/3\nevil')).toBeNull()
  })

  it('still accepts a legitimate target carrying a query string', () => {
    expect(normalizeAuthRedirect('/clubs/3?tab=members')).toBe('/clubs/3?tab=members')
  })

  it('still accepts a legitimate target carrying a fragment', () => {
    expect(normalizeAuthRedirect('/clubs/3#officers')).toBe('/clubs/3#officers')
  })

  it('still accepts a legitimate target with encoded characters', () => {
    expect(normalizeAuthRedirect('/search?q=chess%20club')).toBe('/search?q=chess%20club')
  })

  it('still accepts a legitimate target with a unicode club name', () => {
    expect(normalizeAuthRedirect('/clubs/日本語クラブ')).toBe('/clubs/日本語クラブ')
  })
})

describe('sanitizeAuthRedirectTarget', () => {
  it('falls back to the default landing path for /onboarding', () => {
    expect(sanitizeAuthRedirectTarget('/onboarding')).toBe('/profile')
  })

  it('falls back to the default landing path for /accept-terms (loop guard)', () => {
    expect(sanitizeAuthRedirectTarget('/accept-terms')).toBe('/profile')
  })

  it('falls back to the default landing path for /accept-terms with a query string', () => {
    expect(sanitizeAuthRedirectTarget('/accept-terms?x=1')).toBe('/profile')
  })

  it('falls back to a custom default when provided', () => {
    expect(sanitizeAuthRedirectTarget('/onboarding', '/clubs')).toBe('/clubs')
  })

  it('keeps a valid, unrelated target as-is', () => {
    expect(sanitizeAuthRedirectTarget('/clubs/42')).toBe('/clubs/42')
  })

  it('falls back to the default landing path for a trailing-slash variant of /onboarding', () => {
    expect(sanitizeAuthRedirectTarget('/onboarding/')).toBe('/profile')
  })

  it('falls back to the default landing path for a case variant of /onboarding', () => {
    expect(sanitizeAuthRedirectTarget('/Onboarding')).toBe('/profile')
  })

  it('falls back to the default landing path for a case variant of /accept-terms', () => {
    expect(sanitizeAuthRedirectTarget('/ACCEPT-TERMS')).toBe('/profile')
  })

  it('falls back to the default landing path for a self-referential trailing-slash redirect query', () => {
    expect(sanitizeAuthRedirectTarget('/onboarding/?redirect=/onboarding/')).toBe('/profile')
  })

  it('does not clobber a legitimate target that merely shares a prefix with /onboarding', () => {
    expect(sanitizeAuthRedirectTarget('/onboarding-guide')).toBe('/onboarding-guide')
  })
})

describe('resolvePostAuthRoute', () => {
  it('returns null for an unauthenticated user', () => {
    expect(resolvePostAuthRoute(null, '/clubs')).toBeNull()
  })

  it('sends a brand-new user (terms not accepted, no graduation year) to accept-terms', () => {
    expect(resolvePostAuthRoute({ acceptedTerms: false, graduationYear: null }, '/clubs')).toEqual({
      path: '/accept-terms',
      query: { redirect: '/clubs' },
    })
  })

  it('sends a user who accepted terms but has no graduation year to onboarding', () => {
    expect(resolvePostAuthRoute({ acceptedTerms: true, graduationYear: null }, '/clubs')).toEqual({
      path: '/onboarding',
      query: { redirect: '/clubs' },
    })
  })

  it('sends a fully-onboarded user straight to their target', () => {
    expect(resolvePostAuthRoute({ acceptedTerms: true, graduationYear: 2027 }, '/clubs')).toBe(
      '/clubs',
    )
  })

  it('regression: after accepting terms, a user with no graduation year still goes to onboarding, not the final target', () => {
    // Simulates AcceptTermsView calling the resolver right after refreshUser(),
    // where acceptedTerms is now true but graduationYear has never been set.
    const userAfterAcceptingTerms = { acceptedTerms: true, graduationYear: null }
    expect(resolvePostAuthRoute(userAfterAcceptingTerms, '/profile')).toEqual({
      path: '/onboarding',
      query: { redirect: '/profile' },
    })
  })

  it('does not loop back to a redirect step used as the target', () => {
    expect(
      resolvePostAuthRoute({ acceptedTerms: false, graduationYear: null }, '/onboarding'),
    ).toEqual({ path: '/accept-terms', query: { redirect: '/profile' } })
  })

  it('sends a fully-onboarded user to the default landing path when there is no target', () => {
    expect(resolvePostAuthRoute({ acceptedTerms: true, graduationYear: 2027 }, null)).toBe(
      '/profile',
    )
  })
})

describe('pending auth redirect round-trip', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('saves and consumes a valid redirect', () => {
    savePendingAuthRedirect('/clubs/7')
    expect(consumePendingAuthRedirect()).toBe('/clubs/7')
  })

  it('only consumes a redirect once', () => {
    savePendingAuthRedirect('/clubs/7')
    consumePendingAuthRedirect()
    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('clears a saved redirect so it is no longer consumable', () => {
    savePendingAuthRedirect('/clubs/7')
    clearPendingAuthRedirect()
    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('clearing when nothing is stored is a no-op and does not throw', () => {
    expect(() => clearPendingAuthRedirect()).not.toThrow()
    expect(consumePendingAuthRedirect()).toBeNull()
  })

  it('survives a throwing sessionStorage when saving, consuming, and clearing', () => {
    const originalSessionStorage = window.sessionStorage
    Object.defineProperty(window, 'sessionStorage', {
      configurable: true,
      value: {
        getItem: () => {
          throw new Error('storage disabled')
        },
        setItem: () => {
          throw new Error('storage disabled')
        },
        removeItem: () => {
          throw new Error('storage disabled')
        },
      },
    })

    try {
      expect(() => savePendingAuthRedirect('/clubs/7')).not.toThrow()
      expect(consumePendingAuthRedirect()).toBeNull()
      expect(() => clearPendingAuthRedirect()).not.toThrow()
    } finally {
      Object.defineProperty(window, 'sessionStorage', {
        configurable: true,
        value: originalSessionStorage,
      })
    }
  })
})
