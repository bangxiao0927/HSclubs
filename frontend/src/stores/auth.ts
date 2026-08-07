import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import type { AuthProvider, AuthUser } from '../types/auth'
import {
  fetchAuthProviders,
  fetchAuthenticatedUser,
  logout as apiLogout,
} from '../services/authService'
import { buildApiUrl, setUnauthorizedHandler } from '../services/httpClient'
import { localAvatar, userAvatar } from '../utils/avatarImages'
import { normalizeAuthRedirect, savePendingAuthRedirect } from '../utils/authRedirect'

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

  // The actual /api/auth/me round trip. Every call issues a brand-new
  // request; callers that need de-duplication (see `ensureSessionChecked`
  // below) are responsible for sharing the returned promise themselves.
  const fetchAndApplyUser = async (): Promise<void> => {
    userLoading.value = true
    try {
      const fetched = await fetchAuthenticatedUser()
      currentUser.value = normalizeUser(fetched)
      userError.value = null
    } catch (error) {
      // fetchAuthenticatedUser already separates the two cases: it returns null for a real 401
      // and only throws for a network error or a 5xx. Treating a throw as "signed out" meant one
      // failed request on cold load (offline blip, backend restart, proxy 502) logged the
      // student out of a still-valid session and bounced them off the page they were on, so the
      // previous user is deliberately kept here.
      userError.value =
        error instanceof Error ? error.message : 'Unable to verify session'
    } finally {
      userLoading.value = false
      hasCheckedSession.value = true
    }
  }

  // Post-mutation callers (AcceptTermsView after POSTing accept-terms,
  // OnboardingView/ProfileView after saving a graduation year, App.vue when
  // auth flips true) MUST see the server's current state, not a response
  // some other in-flight caller happened to have kicked off before the mutation
  // landed. So `refreshUser()` always issues a fresh request and never reuses
  // a cached in-flight promise.
  const refreshUser = (): Promise<void> => fetchAndApplyUser()

  // Session checks (main.ts's bootstrap() and the router guard's
  // `if (!hasCheckedSession)` branch) only need "has this browser got a
  // session?" and both fire on cold load before either has resolved. These
  // share a single in-flight /api/auth/me request instead of each issuing
  // their own. The in-flight promise is cleared once the request settles
  // (success or failure), so the next call always genuinely refetches
  // instead of replaying a poisoned/stale result.
  let inFlightSessionCheck: Promise<void> | null = null

  const ensureSessionChecked = (): Promise<void> => {
    if (inFlightSessionCheck) {
      return inFlightSessionCheck
    }
    const request = fetchAndApplyUser().finally(() => {
      inFlightSessionCheck = null
    })
    inFlightSessionCheck = request
    return request
  }

  const bootstrap = async () => {
    await Promise.allSettled([ensureProvidersLoaded(), ensureSessionChecked()])
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

    const authorizationUrl = buildApiUrl(provider.authorizationUrl)
    const normalizedTarget = normalizeAuthRedirect(redirectTarget)
    window.location.href = normalizedTarget
      ? `${authorizationUrl}${authorizationUrl.includes('?') ? '&' : '?'}redirect=${encodeURIComponent(normalizedTarget)}`
      : authorizationUrl
  }

  const logout = async () => {
    try {
      await apiLogout()
      userError.value = null
    } catch (error) {
      // Neither caller catches (App.vue's header button, ProfileView's), so rethrowing here
      // only produced an unhandled rejection. The local session is cleared either way below;
      // record why the server call failed instead of crashing the handler.
      userError.value =
        error instanceof Error ? error.message : 'Sign-out request failed'
    } finally {
      // Signing out must always work client-side. Clearing only on success meant a 401 on an
      // already-dead session, or any network blip, left the app showing the user as signed in
      // as well.
      currentUser.value = null
    }
  }

  // A 401 from any other endpoint means this session is gone; drop the user so the router
  // guards and the header stop treating it as live. Registered once, when the store is first
  // used, since the services have no access to the store themselves.
  setUnauthorizedHandler(() => {
    currentUser.value = null
    hasCheckedSession.value = true
  })

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
    ensureSessionChecked,
    bootstrap,
    beginLogin,
    logout,
  }
})
