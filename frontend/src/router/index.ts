import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '../stores/auth'
import { DEFAULT_POST_AUTH_PATH, resolvePostAuthRoute } from '../utils/authRedirect'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/calendar',
      name: 'calendar',
      component: () => import('../views/CalendarView.vue'),
    },
    {
      path: '/search',
      name: 'club-search',
      component: () => import('../views/ClubSearchView.vue'),
    },
    {
      path: '/clubs/:id',
      name: 'club-detail',
      component: () => import('../views/ClubDetailView.vue'),
    },
    {
      path: '/clubs/:id/admin',
      name: 'club-admin',
      component: () => import('../views/ClubAdminView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/clubs/:id/admin/pending',
      name: 'club-admin-pending',
      component: () => import('../views/ClubPendingView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      name: 'owner-clubs',
      component: () => import('../views/OwnerAdminView.vue'),
      meta: { requiresAuth: true, requiresOwner: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },

    {
      path: '/recommendations',
      name: 'recommendations',
      component: () => import('../views/RecommendationView.vue'),
    },

    // ---- Onboarding ----
    {
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('../views/OnboardingView.vue'),
      meta: { requiresAuth: true },
    },

    // ---- Legal ----
    {
      path: '/terms',
      name: 'terms',
      component: () => import('../views/TermsOfUseView.vue'),
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('../views/PrivacyPolicyView.vue'),
    },
    {
      path: '/accept-terms',
      name: 'accept-terms',
      component: () => import('../views/AcceptTermsView.vue'),
      meta: { requiresAuth: true },
    },

    // ---- Auth routes ----
    {
      path: '/auth',
      name: 'auth-choice',
      component: () => import('../views/AuthChoiceView.vue'),
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('../views/AuthCallbackView.vue'),
    },

    // ---- Invitation ----
    {
      path: '/accept-invitation',
      name: 'accept-invitation',
      component: () => import('../views/AcceptInvitationView.vue'),
    },

    // ---- 404 (must be last) ----
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

const termsBypassRouteNames = new Set([
  'auth-choice',
  'auth-callback',
  'accept-terms',
  'terms',
  'privacy',
  'not-found',
])

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.hasCheckedSession) {
    await authStore.refreshUser()
  }

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const requiresOwner = to.matched.some((record) => record.meta.requiresOwner)

  if (requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'auth-choice',
      query: {
        intent: 'login',
        redirect: to.fullPath,
      },
    }
  }

  // An already-authenticated user landing on the sign-in page (stale link,
  // browser Back, bookmarked /auth?redirect=...) has nothing to choose from
  // here. Route them onward through the same post-auth resolver used after a
  // real login, so a not-yet-onboarded user still lands on /accept-terms or
  // /onboarding instead of skipping those steps.
  if (authStore.isAuthenticated && to.name === 'auth-choice') {
    return resolvePostAuthRoute(authStore.currentUser, to.query.redirect) ?? DEFAULT_POST_AUTH_PATH
  }

  if (
    authStore.isAuthenticated &&
    authStore.currentUser?.acceptedTerms === false &&
    !termsBypassRouteNames.has(String(to.name))
  ) {
    return { name: 'accept-terms', query: { redirect: to.fullPath } }
  }

  if (to.name === 'accept-terms' && authStore.currentUser?.acceptedTerms === true) {
    return resolvePostAuthRoute(authStore.currentUser, to.query.redirect) ?? DEFAULT_POST_AUTH_PATH
  }

  if (requiresOwner && !authStore.currentUser?.isOwner) {
    return { name: 'home' }
  }

  return true
})

export default router
