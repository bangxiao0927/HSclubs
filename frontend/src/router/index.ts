import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '../stores/auth'

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
    },
    {
      path: '/clubs/:id/admin/pending',
      name: 'club-admin-pending',
      component: () => import('../views/ClubPendingView.vue'),
    },
    {
      path: '/admin',
      name: 'owner-clubs',
      component: () => import('../views/OwnerAdminView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
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

  if (to.name === 'accept-terms' && !authStore.isAuthenticated) {
    return { name: 'auth-choice', query: { intent: 'login' } }
  }

  if (
    authStore.isAuthenticated &&
    authStore.currentUser?.acceptedTerms === false &&
    !termsBypassRouteNames.has(String(to.name))
  ) {
    return { name: 'accept-terms' }
  }

  if (to.name === 'accept-terms' && authStore.currentUser?.acceptedTerms === true) {
    return { name: 'home' }
  }

  return true
})

export default router
