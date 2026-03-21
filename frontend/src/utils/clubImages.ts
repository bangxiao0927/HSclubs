import type { Club } from '../types/club'

const fallbackAvatar = (name: string) =>
  `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(name)}`

const instagramAvatar = (url?: string | null) => {
  if (!url) {
    return null
  }

  const normalized = url.trim().replace(/\/+$/, '')
  if (!normalized) {
    return null
  }

  const withoutQuery = normalized.split('?')[0] ?? ''
  const parts = withoutQuery.split('/')
  const lastPart = parts[parts.length - 1] ?? ''
  const handle = lastPart.startsWith('@') ? lastPart.slice(1) : lastPart

  return handle ? `https://unavatar.io/instagram/${encodeURIComponent(handle)}` : null
}

export const clubImage = (club: Club): string =>
  club.imageUrl ?? instagramAvatar(club.instagramUrl) ?? fallbackAvatar(club.name)
