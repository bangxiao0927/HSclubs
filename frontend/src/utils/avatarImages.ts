import { buildApiUrl } from '../services/httpClient'

const palette = [
  ['#0f766e', '#67e8f9'],
  ['#1d4ed8', '#93c5fd'],
  ['#7c3aed', '#c4b5fd'],
  ['#be123c', '#fda4af'],
  ['#a16207', '#fde68a'],
  ['#166534', '#86efac'],
]

const hashSeed = (seed: string) => {
  let hash = 0
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash * 31 + seed.charCodeAt(index)) >>> 0
  }
  return hash
}

const escapeXml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

const initialsFor = (seed: string) => {
  const words = seed
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  const initials = words.length > 1
    ? `${words[0]?.[0] ?? ''}${words[1]?.[0] ?? ''}`
    : seed.trim().slice(0, 2)
  return escapeXml((initials || 'HC').toUpperCase())
}

export const localAvatar = (seed: string) => {
  const normalizedSeed = seed.trim() || 'HS Clubs'
  const [background, foreground] =
    palette[hashSeed(normalizedSeed) % palette.length] ?? ['#0f766e', '#67e8f9']
  const initials = initialsFor(normalizedSeed)
  const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
  <rect width="160" height="160" rx="32" fill="${background}"/>
  <circle cx="124" cy="30" r="48" fill="${foreground}" opacity="0.18"/>
  <circle cx="28" cy="136" r="54" fill="#ffffff" opacity="0.08"/>
  <text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" font-family="Inter, Arial, sans-serif" font-size="54" font-weight="700" fill="${foreground}">${initials}</text>
</svg>`

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

export const userAvatar = (avatarUrl: string | null | undefined, seed: string) => {
  const trimmed = avatarUrl?.trim()
  if (!trimmed || trimmed.includes('api.dicebear.com')) {
    return localAvatar(seed)
  }
  return buildApiUrl(trimmed)
}
