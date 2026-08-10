import { describe, expect, it } from 'vitest'

import { resolveInitialTheme } from './themeBootstrap'

describe('resolveInitialTheme', () => {
  it('uses a valid stored theme over the fallback', () => {
    expect(resolveInitialTheme('dark', 'light')).toBe('dark')
    expect(resolveInitialTheme('light', 'dark')).toBe('light')
  })

  it('falls back to the provided default when nothing valid is stored', () => {
    expect(resolveInitialTheme(null, 'dark')).toBe('dark')
    expect(resolveInitialTheme('system', 'light')).toBe('light')
    expect(resolveInitialTheme('', 'dark')).toBe('dark')
  })
})
