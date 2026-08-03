import type { ClubPostComment, ClubPostFeedPage } from '../types/clubPost'
import { buildApiUrl } from './httpClient'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildApiUrl(path)
  const response = await fetch(url, {
    ...init,
    credentials: init?.credentials ?? 'include',
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }
  const raw = await response.text()
  if (!raw) return undefined as T
  return JSON.parse(raw) as T
}

export const fetchClubMediaFeed = (
  clubIdOrSlug: number | string,
  page: number,
  size: number,
) =>
  request<ClubPostFeedPage>(
    `/api/clubs/${clubIdOrSlug}/posts?page=${page}&size=${size}`,
  )

export const fetchClubPostComments = (
  clubIdOrSlug: number | string,
  postId: number | string,
) =>
  request<ClubPostComment[]>(
    `/api/clubs/${clubIdOrSlug}/posts/${postId}/comments`,
  )
