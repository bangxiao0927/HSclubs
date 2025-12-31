export interface AuthProvider {
  id: string
  name: string
  authorizationUrl: string
}

export interface AuthUser {
  id: string
  email: string
  displayName: string
  avatarUrl: string
  provider: string
}
