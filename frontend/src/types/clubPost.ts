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
  /**
   * Computed by the backend from the viewer's own identity and the club's canManage/platform-
   * owner status (see PublicClubPost#isViewerCanDelete's Javadoc) -- never the author's or the
   * viewer's own oauth_user_id. Drives the delete control without inferring authorship from
   * display names.
   */
  viewerCanDelete: boolean
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
  /** Same capability contract as ClubPost#viewerCanDelete, applied to a single comment. */
  viewerCanDelete: boolean
}

export interface ClubPostFeedPage {
  items: ClubPost[]
  page: number
  size: number
  total: number
}
