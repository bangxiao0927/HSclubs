export interface ClubPost {
  id: number
  clubId: number
  title: string
  imageUrl: string
  pinnedAt: string | null
  /**
   * An unambiguous, offset-bearing ISO-8601 instant (the backend serializes this as a
   * java.time.Instant, always ending in "Z"), not a timezone-naive wall-clock string --
   * safe to hand straight to `new Date(...)`.
   */
  createdAt: string
  authorDisplayName: string
  authorAvatarUrl: string | null
  commentCount: number
}

export interface ClubPostComment {
  id: number
  postId: number
  authorDisplayName: string
  authorAvatarUrl: string | null
  body: string
  createdAt: string
}

export interface ClubPostFeedPage {
  items: ClubPost[]
  page: number
  size: number
  total: number
}
