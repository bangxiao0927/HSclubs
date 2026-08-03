export interface ClubPost {
  id: number
  clubId: number
  title: string
  imageUrl: string
  pinnedAt: string | null
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
