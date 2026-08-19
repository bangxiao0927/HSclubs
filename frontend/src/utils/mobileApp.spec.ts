import { describe, expect, it } from 'vitest'

import { isHSclubsAppUserAgent, MOBILE_AUTH_START_PATH } from './mobileApp'

describe('isHSclubsAppUserAgent', () => {
  it('recognizes the app UA carrying both the name and the protocol marker', () => {
    expect(isHSclubsAppUserAgent('Mozilla/5.0 (iPhone) HSclubsApp/1 (mobile-auth/1)')).toBe(true)
  })

  it('rejects an ordinary browser and a partial marker', () => {
    expect(isHSclubsAppUserAgent('Mozilla/5.0 (iPhone) Safari/605')).toBe(false)
    // Name without the protocol marker must not trigger the app path.
    expect(isHSclubsAppUserAgent('HSclubsApp/1')).toBe(false)
    expect(isHSclubsAppUserAgent(undefined)).toBe(false)
  })

  it('points at the fixed entry the app intercepts', () => {
    expect(MOBILE_AUTH_START_PATH).toBe('/api/mobile-auth/start')
  })
})
