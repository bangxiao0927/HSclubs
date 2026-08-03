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
  /**
   * An unambiguous, offset-bearing ISO-8601 instant, matching ClubPost#createdAt's contract
   * (the backend's PublicClubPostComment#createdAt is a java.time.Instant, always ending in
   * "Z") -- safe to hand straight to `new Date(...)`.
   */
  createdAt: string
}

export interface ClubPostFeedPage {
  items: ClubPost[]
  page: number
  size: number
  total: number
}
