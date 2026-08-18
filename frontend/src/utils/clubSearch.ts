import type { Club } from '../types/club'

const normalizeClubSearchText = (value: string) =>
  value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase()

export const normalizeClubSearchQuery = (value: unknown) =>
  typeof value === 'string' ? normalizeClubSearchText(value) : ''

// Split on any run of whitespace so "chess  club" (a double space, or a pasted line break)
// behaves like "chess club" instead of matching nothing.
export const clubSearchTokens = (query: string) => query.split(/\s+/).filter(Boolean)

export const matchesClubSearch = (club: Club, query: string) => {
  if (!query) {
    return true
  }

  const instagramKeywords = new Set(['ins', 'instagram'])
  const hasInstagramShortcutMatch =
    instagramKeywords.has(query) && Boolean(club.instagramUrl?.trim())

  const haystacks = [
    club.name,
    club.slug,
    club.aliasName,
    club.description,
    club.category,
    club.meetingSchedule,
    club.scheduleNote,
    club.location,
    club.contactEmail,
    club.advisor,
    club.instagramUrl,
    ...(club.achievements ?? []),
  ]
    .filter((value): value is string => Boolean(value))
    .map(normalizeClubSearchText)

  // Every word must match something (AND across words), but each word may match a different
  // field (OR across fields). Testing the whole query as one substring meant the words had to
  // be contiguous and in order inside a single field, so ordinary queries returned nothing:
  // "club chess" missed "Chess Club", and "robotics wednesday" missed a robotics club that
  // meets Wednesday, because the name and the schedule are separate fields.
  return (
    hasInstagramShortcutMatch ||
    clubSearchTokens(query).every((token) => haystacks.some((value) => value.includes(token)))
  )
}
