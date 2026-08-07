import type { Club } from '../types/club'

export const normalizeClubSearchQuery = (value: unknown) =>
  typeof value === 'string' ? value.trim().toLowerCase() : ''

// Split on any run of whitespace so "chess  club" (a double space, or a pasted line break)
// behaves like "chess club" instead of matching nothing.
export const clubSearchTokens = (query: string) => query.split(/\s+/).filter(Boolean)

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

  // Every word must match something (AND across words), but each word may match a different
  // field (OR across fields). Testing the whole query as one substring meant the words had to
  // be contiguous and in order inside a single field, so ordinary queries returned nothing:
  // "club chess" missed "Chess Club", and "robotics wednesday" missed a robotics club that
  // meets Wednesday, because the name and the schedule are separate fields.
  return clubSearchTokens(query).every((token) =>
    haystacks.some((value) => value.includes(token)),
  )
}
