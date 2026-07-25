import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import type { AuthProvider, AuthUser } from '../types/auth'
import {
  fetchAuthProviders,
  fetchAuthenticatedUser,
  logout as apiLogout,
} from '../services/authService'
import { buildApiUrl } from '../services/httpClient'
import { localAvatar, userAvatar } from '../utils/avatarImages'
import { savePendingAuthRedirect } from '../utils/authRedirect'

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
    if (providersLoaded.value || providersLoading.value) return
    providersLoading.value = true
    try {
      providers.value = await fetchAuthProviders()
      providersError.value = null
      providersLoaded.value = true
    } catch (error) {
      providersError.value =
        error instanceof Error ? error.message : 'Unable to load providers'
    } finally {
      providersLoading.value = false
    }
  }

  const buildFallbackAvatar = (user: AuthUser) => {
    const seed = user.displayName?.trim() || user.email || user.id || 'Member'
    return localAvatar(seed)
  }

  const normalizeUser = (user: AuthUser | null): AuthUser | null => {
    if (!user) return null
    const trimmed = user.avatarUrl?.trim()
    const seed = user.displayName?.trim() || user.email || user.id || 'Member'
    return {
      ...user,
      avatarUrl: trimmed ? userAvatar(trimmed, seed) : buildFallbackAvatar(user),
      // Support both isOwner (backward compat) and isPlatformOwner
      isOwner: user.isOwner ?? user.isPlatformOwner ?? false,
    }
  }

  // Concurrent callers (e.g. main.ts's bootstrap() and the router guard both
  // firing on cold load, before either has resolved) must share a single
  // /api/auth/me request instead of each issuing their own. The in-flight
  // promise is cached here and cleared once the request settles, so a later
  // explicit refreshUser() call still triggers a fresh request.
  let inFlightRefresh: Promise<void> | null = null

  const refreshUser = (): Promise<void> => {
    if (inFlightRefresh) {
      return inFlightRefresh
    }

    const request = (async () => {
      userLoading.value = true
      try {
        const fetched = await fetchAuthenticatedUser()
        currentUser.value = normalizeUser(fetched)
        userError.value = null
      } catch (error) {
        userError.value =
          error instanceof Error ? error.message : 'Unable to verify session'
        currentUser.value = null
      } finally {
        userLoading.value = false
        hasCheckedSession.value = true
        inFlightRefresh = null
      }
    })()

    inFlightRefresh = request
    return request
  }

  const bootstrap = async () => {
    await Promise.allSettled([ensureProvidersLoaded(), refreshUser()])
  }

  const beginLogin = (providerId: string, redirectTarget?: string | null) => {
    const sanitizedId = providerId?.trim()
    if (!sanitizedId) {
      providersError.value =
        'No OAuth provider was selected. Please refresh and try again.'
      return
    }
    const provider = providers.value.find((item) => item.id === sanitizedId)
    if (!provider?.authorizationUrl) {
      providersError.value =
        'This sign-in provider is not configured correctly. Please refresh and try again.'
      return
    }
    savePendingAuthRedirect(redirectTarget)
    window.location.href = buildApiUrl(provider.authorizationUrl)
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
