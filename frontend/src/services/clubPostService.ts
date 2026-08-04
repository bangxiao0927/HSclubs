import type { ClubPost, ClubPostComment, ClubPostFeedPage } from '../types/clubPost'
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

// Deliberately not routed through request(): that helper does not set a Content-Type, but a
// caller could still be tempted to add one for a "JSON-like" POST. FormData needs the browser
// to supply its own multipart boundary in Content-Type, so this mirrors
// ClubAdminView#handleImageUpload's raw fetch exactly rather than reusing request().
export const publishClubPost = async (
  clubIdOrSlug: number | string,
  title: string,
  file: File,
): Promise<ClubPost> => {
  const formData = new FormData()
  formData.append('title', title)
  formData.append('file', file)
  const response = await fetch(buildApiUrl(`/api/clubs/${clubIdOrSlug}/posts`), {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }
  return (await response.json()) as ClubPost
}

export const deleteClubPost = (clubIdOrSlug: number | string, postId: number | string) =>
  request<void>(`/api/clubs/${clubIdOrSlug}/posts/${postId}`, { method: 'DELETE' })

export const pinClubPost = (clubIdOrSlug: number | string, postId: number | string) =>
  request<void>(`/api/clubs/${clubIdOrSlug}/posts/${postId}/pin`, { method: 'PUT' })

export const unpinClubPost = (clubIdOrSlug: number | string, postId: number | string) =>
  request<void>(`/api/clubs/${clubIdOrSlug}/posts/${postId}/pin`, { method: 'DELETE' })

export const createClubPostComment = (
  clubIdOrSlug: number | string,
  postId: number | string,
  body: string,
) =>
  request<ClubPostComment>(`/api/clubs/${clubIdOrSlug}/posts/${postId}/comments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ body }),
  })

export const deleteClubPostComment = (
  clubIdOrSlug: number | string,
  postId: number | string,
  commentId: number | string,
) =>
  request<void>(`/api/clubs/${clubIdOrSlug}/posts/${postId}/comments/${commentId}`, {
    method: 'DELETE',
  })
