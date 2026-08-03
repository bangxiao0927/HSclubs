import type { Club } from '../types/club'
import type { ClubPost } from '../types/clubPost'
import { buildApiUrl } from '../services/httpClient'
import { localAvatar } from './avatarImages'

type ClubImageSource = Pick<Club, 'id' | 'name' | 'imageUrl'> &
  Partial<Pick<Club, 'instagramUrl' | 'createdAt' | 'updatedAt'>>

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

const cachedInstagramAvatar = (club: ClubImageSource) => {
  const handle = instagramHandle(club.instagramUrl)
  if (!handle) {
    return null
  }
  const version = encodeURIComponent(`${club.id}:${club.updatedAt ?? club.createdAt ?? club.instagramUrl ?? handle}`)
  return buildApiUrl(`/api/avatars/instagram/${encodeURIComponent(handle)}?v=${version}`)
}

export const clubImage = (club: ClubImageSource): string => {
  if (club.imageUrl) {
    return buildApiUrl(club.imageUrl)
  }
  return cachedInstagramAvatar(club) ?? localAvatar(club.name)
}

// club_post.image_url is always server-relative (see ClubPostService's Javadoc); routing it
// through buildApiUrl keeps photos loading when VITE_API_BASE_URL is an absolute, different
// origin than the SPA itself.
export const clubPostImage = (post: Pick<ClubPost, 'imageUrl'>): string => buildApiUrl(post.imageUrl)
