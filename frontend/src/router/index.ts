import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '../stores/auth'
import { DEFAULT_POST_AUTH_PATH, resolvePostAuthRoute } from '../utils/authRedirect'
import {
  clearStaleChunkRecovery,
  isStaleChunkError,
  recoverFromStaleChunk,
} from '../utils/staleChunk'

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
      path: '/clubs/:id/media',
      name: 'club-media',
      // Club media now renders embedded on the club detail page (see
      // ClubDetailView.vue's #media section). This route is kept only so
      // pre-existing links and bookmarks still land somewhere useful.
      redirect: (to) => ({
        path: `/clubs/${to.params.id}`,
        query: to.query,
        hash: '#media',
      }),
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
      // Pending membership requests are now reviewed inline in the Members section of
      // /clubs/:id/admin (see ClubAdminView.vue's #members section). This route is kept only
      // so pre-existing links and bookmarks still land somewhere useful.
      redirect: (to) => ({
        path: `/clubs/${to.params.id}/admin`,
        query: to.query,
        hash: '#members',
      }),
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
    {
      path: '/auth/password',
      name: 'auth-password',
      component: () => import('../views/PasswordSignInView.vue'),
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
  'auth-password',
  'accept-terms',
  'terms',
  'privacy',
  'not-found',
])

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.hasCheckedSession) {
    await authStore.ensureSessionChecked()
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
    // An authenticated visit to /auth must not leave /auth sitting in
    // history: forward with `replace: true` so the (now-pointless) /auth
    // entry doesn't trap the Back button behind the real destination.
    //
    // The destination is resolved (via `router.resolve`) before being
    // returned rather than being returned as-is: `resolvePostAuthRoute` can
    // return a bare string carrying its own query string and/or hash (e.g.
    // `/clubs/3?ref=email`), and wrapping that string directly in
    // `{ path: destination }` would make vue-router silently drop everything
    // after the path. Resolving first and re-expressing the result as
    // `{ path, query, hash }` keeps the query and hash intact for both the
    // bare-string and object-shaped (`{ path, query }`) cases.
    const destination =
      resolvePostAuthRoute(authStore.currentUser, to.query.redirect) ?? DEFAULT_POST_AUTH_PATH
    const resolved = router.resolve(destination)
    return {
      path: resolved.path,
      query: resolved.query,
      hash: resolved.hash,
      replace: true,
    }
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

// A navigation that dies because its lazily-imported view chunk no longer
// exists (see utils/staleChunk.ts) otherwise looks like a dead button: the URL
// never changes and nothing is rendered. Reload at the requested URL so the
// browser picks up the current chunk manifest and the student can carry on.
router.onError((error, to) => {
  if (isStaleChunkError(error)) {
    recoverFromStaleChunk(to.fullPath)
  }
})

// Only a navigation that actually landed proves the chunks are loadable again.
// vue-router also runs afterEach for aborted/duplicated navigations (it carries
// the failure as the third argument), and clearing the marker on one of those
// would hand back a fresh reload attempt for a chunk that is still missing.
router.afterEach((_to, _from, failure) => {
  if (!failure) {
    clearStaleChunkRecovery()
  }
})

export default router
