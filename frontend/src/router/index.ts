import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import PlatformHomeView from '../views/PlatformHomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ---- Legacy routes (backward compat, redirect to MVHS eventually) ----
    {
      path: '/',
      name: 'home',
      component: PlatformHomeView,
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

    // ---- School-scoped routes ----
    {
      path: '/schools',
      name: 'school-picker',
      component: () => import('../views/SchoolPickerView.vue'),
    },
    {
      path: '/schools/:schoolSlug',
      name: 'school-home',
      component: HomeView,
    },
    {
      path: '/schools/:schoolSlug/search',
      name: 'school-club-search',
      component: () => import('../views/ClubSearchView.vue'),
    },
    {
      path: '/schools/:schoolSlug/calendar',
      name: 'school-calendar',
      component: () => import('../views/CalendarView.vue'),
    },
    {
      path: '/schools/:schoolSlug/categories',
      name: 'school-about',
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/schools/:schoolSlug/clubs/:clubSlugOrId',
      name: 'school-club-detail',
      component: () => import('../views/ClubDetailView.vue'),
    },
    {
      path: '/schools/:schoolSlug/clubs/:clubSlugOrId/admin',
      name: 'school-club-admin',
      component: () => import('../views/ClubAdminView.vue'),
    },
    {
      path: '/schools/:schoolSlug/clubs/:clubSlugOrId/admin/pending',
      name: 'school-club-admin-pending',
      component: () => import('../views/ClubPendingView.vue'),
    },
    {
      path: '/schools/:schoolSlug/admin',
      name: 'school-owner-clubs',
      component: () => import('../views/OwnerAdminView.vue'),
    },
    {
      path: '/schools/:schoolSlug/profile',
      name: 'school-profile',
      component: () => import('../views/ProfileView.vue'),
    },

    // ---- Platform routes ----
    {
      path: '/platform/admin',
      name: 'platform-admin',
      component: () => import('../views/PlatformAdminView.vue'),
    },

    // ---- Invitation ----
    {
      path: '/accept-invitation',
      name: 'accept-invitation',
      component: () => import('../views/AcceptInvitationView.vue'),
    },

    // ---- Settings ----
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

    // ---- 404 (must be last) ----
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

export default router
