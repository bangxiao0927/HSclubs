import type { Club } from '../types/club'
import { buildApiUrl } from '../services/httpClient'
import { localAvatar } from './avatarImages'

const instagramHandle = (url?: string | null) => {
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

  return handle || null
}

const cachedInstagramAvatar = (url?: string | null) => {
  const handle = instagramHandle(url)
  return handle ? buildApiUrl(`/api/avatars/instagram/${encodeURIComponent(handle)}`) : null
}

export const clubImage = (club: Club): string => {
  if (club.imageUrl) {
    return buildApiUrl(club.imageUrl)
  }
  return cachedInstagramAvatar(club.instagramUrl) ?? localAvatar(club.name)
}
