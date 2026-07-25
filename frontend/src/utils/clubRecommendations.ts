import type { Club } from '../types/club'

export type ScoredCategory = {
  title: string
  score: number
}

export const selectWeightedClubRecommendations = (
  clubs: Club[],
  categories: ScoredCategory[],
  limit = 12,
) => {
  const categoryQueues = categories
    .filter((category) => category.score > 0)
    .map((category, rank) => ({
      score: category.score,
      rank,
      clubs: clubs
        .filter((club) => club.category === category.title)
        .sort((a, b) => {
          const memberDelta = (b.memberCount ?? 0) - (a.memberCount ?? 0)
          return memberDelta !== 0 ? memberDelta : a.name.localeCompare(b.name)
        }),
      nextIndex: 0,
    }))
  const matches: Club[] = []

  while (matches.length < limit) {
    const availableQueues = categoryQueues.filter((queue) => queue.clubs[queue.nextIndex])
    if (!availableQueues.length) break

    availableQueues.sort((a, b) => {
      const allocationDelta = a.nextIndex / a.score - b.nextIndex / b.score
      return allocationDelta || a.rank - b.rank
    })

    const queue = availableQueues[0]!
    matches.push(queue.clubs[queue.nextIndex]!)
    queue.nextIndex++
  }

  return matches
}
