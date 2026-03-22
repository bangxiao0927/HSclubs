export interface Club {
  id: number
  name: string
  aliasName: string | null
  description: string
  category: string
  meetingSchedule: string
  scheduleNote?: string | null
  location: string | null
  contactEmail: string | null
  advisor: string | null
  imageUrl: string | null
  instagramUrl?: string | null
  memberCount: number
  achievements: string[]
  schoolId: number
  viewerRole?: string | null
  viewerIsMember?: boolean
  canManage?: boolean
  viewerHasPendingRequest?: boolean
}

export interface ClubMember {
  oauthUserId: number
  displayName: string
  email: string
  avatarUrl: string | null
  roleName: string | null
}

export interface ClubMembershipRequest {
  id: number
  clubId: number
  oauthUserId: number
  displayName: string | null
  email: string | null
  avatarUrl: string | null
  createdAt: string
}
