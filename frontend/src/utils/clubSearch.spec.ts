import { describe, expect, it } from 'vitest'

import type { Club } from '../types/club'
import { matchesClubSearch, normalizeClubSearchQuery } from './clubSearch'

const makeClub = (overrides: Partial<Club> = {}): Club => ({
  id: 1,
  name: 'Chess Club',
  aliasName: null,
  description: 'Casual and competitive play',
  category: 'Competition & Strategy',
  meetingSchedule: 'Wednesday lunch',
  location: 'Room 214',
  contactEmail: null,
  advisor: 'Ms. Lee',
  imageUrl: null,
  memberCount: 0,
  achievements: [],
  ...overrides,
})

const matches = (club: Club, rawQuery: string) =>
  matchesClubSearch(club, normalizeClubSearchQuery(rawQuery))

describe('matchesClubSearch', () => {
  it('matches a single word in any field', () => {
    expect(matches(makeClub(), 'chess')).toBe(true)
    expect(matches(makeClub(), 'wednesday')).toBe(true)
    expect(matches(makeClub(), 'lee')).toBe(true)
  })

  // The words a student types are rarely in the club's own order.
  it('ignores word order', () => {
    expect(matches(makeClub(), 'club chess')).toBe(true)
  })

  // Name and schedule are different fields, so requiring one contiguous substring never
  // matched this at all.
  it('matches words that live in different fields', () => {
    expect(matches(makeClub({ name: 'Robotics Team' }), 'robotics wednesday')).toBe(true)
  })

  it('tolerates extra whitespace between words', () => {
    expect(matches(makeClub(), 'chess  club')).toBe(true)
  })

  it('still requires every word to match something', () => {
    expect(matches(makeClub(), 'chess badminton')).toBe(false)
  })

  it('still matches partial words', () => {
    expect(matches(makeClub(), 'che')).toBe(true)
  })

  it('matches every club for an empty query', () => {
    expect(matches(makeClub(), '   ')).toBe(true)
  })

  it('keeps the instagram keyword shortcut', () => {
    expect(matches(makeClub({ instagramUrl: 'https://instagram.com/chess' }), 'ins')).toBe(true)
    expect(matches(makeClub({ instagramUrl: null }), 'instagram')).toBe(false)
  })
})
