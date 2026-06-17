export interface AuthProvider {
  id: string
  name: string
  authorizationUrl: string
}

export interface SchoolMembership {
  schoolId: number
  slug: string
  schoolName: string
  role: string
  status: string
}

export interface HomeSchool {
  schoolId: number
  slug: string
  schoolName: string
}

export interface AuthUser {
  id: string
  email: string
  displayName: string
  avatarUrl: string
  provider: string
  isOwner: boolean
  isPlatformOwner?: boolean
  graduationYear?: number | null
  homeSchool?: HomeSchool | null
  schoolMemberships?: SchoolMembership[]
}
