import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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

export default router
