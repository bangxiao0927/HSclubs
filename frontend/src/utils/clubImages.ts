import type { Club } from '../types/club'
import { buildApiUrl } from '../services/httpClient'
import { localAvatar } from './avatarImages'

const instagramHandle = (url?: string | null) => {
  if (!url) {
    return null
  }

  const trimmed = url.trim()
  if (!trimmed) {
    return null
  }

  const cleanHandle = (value: string) => {
    const handle = value.trim().replace(/^@/, '')
    return /^[A-Za-z0-9._]{1,64}$/.test(handle) ? handle : null
  }

  const directHandle = cleanHandle(trimmed)
  if (directHandle) {
    return directHandle
  }

  const normalizedUrl = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`
  try {
    const parsed = new URL(normalizedUrl)
    const host = parsed.hostname.toLowerCase().replace(/^www\./, '')
    if (!host.endsWith('instagram.com')) {
      return null
    }
    const firstSegment = parsed.pathname
      .split('/')
      .map((part) => part.trim())
      .filter(Boolean)[0]
    if (!firstSegment || ['p', 'reel', 'reels', 'stories', 'explore'].includes(firstSegment)) {
      return null
    }
    return cleanHandle(firstSegment)
  } catch {
    const withoutQuery = trimmed.split('?')[0]?.replace(/\/+$/, '') ?? ''
    const parts = withoutQuery.split('/').filter(Boolean)
    const lastPart = parts[parts.length - 1] ?? ''
    return cleanHandle(lastPart)
  }
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
