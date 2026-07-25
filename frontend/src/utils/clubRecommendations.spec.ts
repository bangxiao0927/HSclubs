import { describe, expect, it } from 'vitest'

import type { Club } from '../types/club'
import { selectWeightedClubRecommendations } from './clubRecommendations'

const makeClub = (id: number, category: string, memberCount = 0): Club => ({
  id,
  name: `Club ${id}`,
  aliasName: null,
  description: '',
  category,
  meetingSchedule: '',
  location: null,
  contactEmail: null,
  advisor: null,
  imageUrl: null,
  memberCount,
  achievements: [],
})

describe('selectWeightedClubRecommendations', () => {
  it('allocates recommendations in proportion to category scores', () => {
    const clubs = [
      ...Array.from({ length: 12 }, (_, index) => makeClub(index + 1, 'Primary')),
      ...Array.from({ length: 12 }, (_, index) => makeClub(index + 101, 'Secondary')),
    ]

    const recommendations = selectWeightedClubRecommendations(
      clubs,
      [
        { title: 'Primary', score: 12 },
        { title: 'Secondary', score: 4 },
      ],
      12,
    )

    expect(recommendations.filter((club) => club.category === 'Primary')).toHaveLength(9)
    expect(recommendations.filter((club) => club.category === 'Secondary')).toHaveLength(3)
  })

  it('excludes categories with a zero score', () => {
    const recommendations = selectWeightedClubRecommendations(
      [makeClub(1, 'Matched'), makeClub(2, 'Unmatched')],
      [
        { title: 'Matched', score: 3 },
        { title: 'Unmatched', score: 0 },
      ],
    )

    expect(recommendations.map((club) => club.category)).toEqual(['Matched'])
  })

  it('uses lower-ranked categories when a stronger match runs out of clubs', () => {
    const recommendations = selectWeightedClubRecommendations(
      [makeClub(1, 'Primary'), makeClub(2, 'Secondary'), makeClub(3, 'Secondary')],
      [
        { title: 'Primary', score: 6 },
        { title: 'Secondary', score: 2 },
      ],
      3,
    )

    expect(recommendations).toHaveLength(3)
    expect(recommendations.filter((club) => club.category === 'Secondary')).toHaveLength(2)
  })
})
