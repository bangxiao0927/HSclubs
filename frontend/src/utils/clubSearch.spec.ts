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

  it.each([
    ['slug', { slug: 'chess-society' }, 'society'],
    ['alias', { aliasName: 'MV Chess' }, 'mv'],
    ['description', { description: 'Learn tactical openings' }, 'tactical'],
    ['category', { category: 'Strategy Games' }, 'strategy'],
    ['meeting schedule', { meetingSchedule: 'Friday lunch' }, 'friday'],
    ['schedule note', { scheduleNote: 'Meet every other week' }, 'other'],
    ['location', { location: 'Library conference room' }, 'conference'],
    ['contact email', { contactEmail: 'chess@mvla.net' }, 'mvla'],
    ['advisor', { advisor: 'Dr. Nguyen' }, 'nguyen'],
    ['Instagram URL', { instagramUrl: 'https://instagram.com/mvchess' }, 'mvchess'],
    ['achievements', { achievements: ['Regional champions'] }, 'champions'],
  ] satisfies Array<[string, Partial<Club>, string]>)(
    'matches the %s field',
    (_field, values, query) => {
      expect(matches(makeClub(values), query)).toBe(true)
    },
  )

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

  it('matches names and queries regardless of diacritics', () => {
    expect(matches(makeClub({ name: 'Élan Dance Club' }), 'elan')).toBe(true)
    expect(matches(makeClub({ advisor: 'Jose Alvarez' }), 'José')).toBe(true)
  })

  it('matches every club for an empty query', () => {
    expect(matches(makeClub(), '   ')).toBe(true)
  })

  it('keeps the instagram keyword shortcut', () => {
    expect(matches(makeClub({ instagramUrl: 'https://instagram.com/chess' }), 'ins')).toBe(true)
    expect(matches(makeClub({ instagramUrl: null }), 'instagram')).toBe(false)
  })

  it('does not let the instagram shortcut hide ordinary field matches', () => {
    expect(matches(makeClub({ name: 'INS Club', instagramUrl: null }), 'ins')).toBe(true)
  })
})
