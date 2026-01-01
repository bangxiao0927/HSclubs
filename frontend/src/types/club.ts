export interface Club {
  id: number
  name: string
  aliasName: string | null
  description: string
  category: string
  meetingSchedule: string
  location: string | null
  contactEmail: string | null
  advisor: string | null
  imageUrl: string | null
  memberCount: number
  achievements: string[]
  schoolId: number
  viewerRole?: string | null
  canManage?: boolean
}
