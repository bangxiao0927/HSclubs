import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import type { AuthProvider, AuthUser } from '../types/auth'
import { fetchAuthProviders, fetchAuthenticatedUser, logout as apiLogout } from '../services/authService'
import { buildApiUrl } from '../services/httpClient'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<AuthUser | null>(null)
  const providers = ref<AuthProvider[]>([])
  const providersLoading = ref(false)
  const providersLoaded = ref(false)
  const providersError = ref<string | null>(null)
  const userLoading = ref(false)
  const userError = ref<string | null>(null)
  const hasCheckedSession = ref(false)

  const isAuthenticated = computed(() => currentUser.value !== null)

  const ensureProvidersLoaded = async () => {
    if (providersLoaded.value || providersLoading.value) {
      return
    }

    providersLoading.value = true
    try {
      providers.value = await fetchAuthProviders()
      providersError.value = null
      providersLoaded.value = true
    } catch (error) {
      providersError.value = error instanceof Error ? error.message : 'Unable to load providers'
    } finally {
      providersLoading.value = false
    }
  }

  const buildFallbackAvatar = (user: AuthUser) => {
    const seed = user.displayName?.trim() || user.email || user.id || 'Member'
    return `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(seed)}`
  }

  const normalizeUser = (user: AuthUser | null): AuthUser | null => {
    if (!user) {
      return null
    }

    const trimmed = user.avatarUrl?.trim()
    return {
      ...user,
      avatarUrl: trimmed ? buildApiUrl(trimmed) : buildFallbackAvatar(user),
    }
  }

  const refreshUser = async () => {
    userLoading.value = true
    try {
      const fetched = await fetchAuthenticatedUser()
      currentUser.value = normalizeUser(fetched)
      userError.value = null
    } catch (error) {
      userError.value = error instanceof Error ? error.message : 'Unable to verify session'
      currentUser.value = null
    } finally {
      userLoading.value = false
      hasCheckedSession.value = true
    }
  }

  const bootstrap = async () => {
    await Promise.allSettled([ensureProvidersLoaded(), refreshUser()])
  }

  const beginLogin = (providerId: string) => {
    const sanitizedId = providerId?.trim()
    if (!sanitizedId) {
      providersError.value = 'No OAuth provider was selected. Please refresh and try again.'
      return
    }

    const provider = providers.value.find((item) => item.id === sanitizedId)
    const authorizationPath = provider?.authorizationUrl ?? `/oauth2/authorization/${sanitizedId}`
    window.location.href = buildApiUrl(authorizationPath)
  }

  const logout = async () => {
    await apiLogout()
    currentUser.value = null
  }

  return {
    currentUser,
    providers,
    providersLoading,
    providersError,
    userLoading,
    userError,
    hasCheckedSession,
    isAuthenticated,
    ensureProvidersLoaded,
    refreshUser,
    bootstrap,
    beginLogin,
    logout,
  }
})
