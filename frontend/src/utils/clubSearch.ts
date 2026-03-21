import type { Club } from '../types/club'

export const normalizeClubSearchQuery = (value: unknown) =>
  typeof value === 'string' ? value.trim().toLowerCase() : ''

export const matchesClubSearch = (club: Club, query: string) => {
  if (!query) {
    return true
  }

  const instagramKeywords = new Set(['ins', 'instagram'])
  if (instagramKeywords.has(query)) {
    return Boolean(club.instagramUrl?.trim())
  }

  const haystacks = [
    club.name,
    club.aliasName,
    club.description,
    club.category,
    club.meetingSchedule,
    club.location,
    club.contactEmail,
    club.advisor,
    club.instagramUrl,
  ]
    .filter((value): value is string => Boolean(value))
    .map((value) => value.toLowerCase())

  return haystacks.some((value) => value.includes(query))
}
