<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'
import { useSchoolStore } from './stores/school'

const searchQuery = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const schoolStore = useSchoolStore()
const { isAuthenticated, currentUser } = storeToRefs(authStore)
const { currentSchool, currentSchoolSlug } = storeToRefs(schoolStore)

const schoolSlug = computed(() => {
  const slug = route.params.schoolSlug
  return typeof slug === 'string' ? slug : ''
})

// Sync school context from route
watch(
  schoolSlug,
  (slug) => {
    if (slug && slug !== currentSchoolSlug.value) {
      schoolStore.setCurrentSchoolBySlug(slug)
    }
  },
  { immediate: true },
)

const navHome = computed(() => (schoolSlug.value ? `/schools/${schoolSlug.value}` : '/'))
const navCategories = computed(() =>
  schoolSlug.value ? `/schools/${schoolSlug.value}/categories` : '/about',
)
const navCalendar = computed(() =>
  schoolSlug.value ? `/schools/${schoolSlug.value}/calendar` : '/calendar',
)
const navAdmin = computed(() =>
  schoolSlug.value ? `/schools/${schoolSlug.value}/admin` : '/admin',
)
const navProfile = computed(() =>
  schoolSlug.value ? `/schools/${schoolSlug.value}/profile` : '/profile',
)

const handleLogout = () => {
  authStore.logout()
}

const theme = ref<'light' | 'dark'>('light')
const themeLabel = computed(() => (theme.value === 'light' ? 'Dark mode' : 'Light mode'))

const applyTheme = (value: 'light' | 'dark') => {
  document.documentElement.dataset.theme = value
  try {
    localStorage.setItem('theme', value)
  } catch (error) {
    console.warn('Failed to persist theme preference.', error)
  }
}

const toggleTheme = () => {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
}

try {
  const storedTheme = localStorage.getItem('theme')
  if (storedTheme === 'light' || storedTheme === 'dark') {
    theme.value = storedTheme
  }
} catch (error) {
  console.warn('Failed to read theme preference.', error)
}

const syncSearchQueryFromRoute = () => {
  searchQuery.value = typeof route.query.q === 'string' ? route.query.q : ''
}

const submitSearch = () => {
  const q = searchQuery.value.trim()
  if (schoolSlug.value) {
    void router.push({
      name: q ? 'school-club-search' : 'school-home',
      params: { schoolSlug: schoolSlug.value },
      query: q ? { q } : {},
    })
  } else {
    void router.push({
      name: q ? 'club-search' : 'home',
      query: q ? { q } : {},
    })
  }
}

watch(
  isAuthenticated,
  (authenticated, previous) => {
    if (authenticated && !previous) {
      void authStore.refreshUser()
    }
  },
)

watch(theme, (value) => applyTheme(value), { immediate: true })

watch(
  () => route.query.q,
  () => {
    syncSearchQueryFromRoute()
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-shell">
    <header class="header">
      <div class="header-inner page-shell">
        <div class="header-left">
          <div class="logo">
            <RouterLink to="/schools" class="logo-link">
              <img class="logo-icon" src="/android-chrome-512x512.png" alt="HS Clubs logo" />
              <span class="logo-text">{{ currentSchool?.shortName || currentSchool?.schoolName || 'HS Clubs' }}</span>
            </RouterLink>
          </div>
          <nav class="nav">
            <RouterLink
              :to="navHome"
              class="nav-link"
              :class="{ active: route.name === 'home' || route.name === 'school-home' }"
            >Home</RouterLink>
            <RouterLink
              :to="navCategories"
              class="nav-link"
              :class="{ active: route.name === 'about' || route.name === 'school-about' }"
            >Category</RouterLink>
            <RouterLink
              :to="navCalendar"
              class="nav-link"
              :class="{ active: route.name === 'calendar' || route.name === 'school-calendar' }"
            >Calendar</RouterLink>
            <RouterLink
              :to="navAdmin"
              v-if="currentUser?.isOwner"
              class="nav-link"
              :class="{ active: route.name === 'owner-clubs' || route.name === 'school-owner-clubs' }"
            >Admin</RouterLink>
          </nav>
        </div>

        <form class="search-bar" @submit.prevent="submitSearch">
          <input
            v-model="searchQuery"
            type="search"
            placeholder="Search your favorite clubs"
            class="search-input"
          />
          <button type="submit" class="search-button" aria-label="Search clubs">
            <span class="search-icon">🔍</span>
          </button>
        </form>

        <div class="header-right">
          <RouterLink
            v-if="schoolSlug"
            to="/schools"
            class="nav-link school-switch"
          >Change school</RouterLink>
          <button type="button" class="theme-toggle" @click="toggleTheme">
            <span class="theme-icon" aria-hidden="true">{{ theme === 'light' ? '🌙' : '☀️' }}</span>
            <span>{{ themeLabel }}</span>
          </button>
          <RouterLink v-if="isAuthenticated" :to="navProfile" class="profile-link">
            <span class="profile-icon">👤</span>
            <span>Profile</span>
          </RouterLink>
          <div v-if="!isAuthenticated" class="auth-actions">
            <RouterLink to="/auth?intent=login" class="auth-btn ghost">Log in</RouterLink>
            <RouterLink to="/auth?intent=register" class="auth-btn primary">Register</RouterLink>
          </div>
          <button
            v-else
            type="button"
            class="auth-btn ghost logout-btn"
            @click="handleLogout"
          >Log out</button>
        </div>
      </div>
    </header>

    <main class="view-container">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.header {
  padding-block: 1rem;
  background: var(--mv-header-bg);
  border-bottom: 1px solid var(--mv-header-border);
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(12px);
  box-shadow: var(--mv-header-shadow);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.25rem;
}

.page-shell {
  width: var(--page-content-width);
  margin: 0 auto;
  padding-inline: var(--page-padding-inline);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.logo-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
  color: inherit;
}

.logo-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--mv-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: var(--mv-text);
}

.logo-text {
  font-weight: 700;
  color: var(--mv-logo-text);
}

.nav {
  display: flex;
  align-items: center;
  gap: clamp(1rem, 2vw, 2rem);
  font-size: 0.95rem;
  flex-wrap: wrap;
}

.nav-link {
  color: var(--mv-nav-text);
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link.active {
  color: var(--mv-nav-text-active);
  font-weight: 600;
}

.nav-link:hover {
  color: var(--mv-nav-text-hover);
}

.school-switch {
  font-size: 0.85rem;
  opacity: 0.75;
}

.search-bar {
  display: flex;
  align-items: center;
  background: var(--mv-search-bg);
  border-radius: 999px;
  padding: 0.42rem 0.6rem 0.42rem 1rem;
  flex: 1 1 260px;
  min-width: 220px;
  max-width: 440px;
  gap: 0.5rem;
  border: 1px solid var(--mv-search-border);
}

.search-input {
  border: none;
  outline: none;
  flex: 1;
  font-size: 0.95rem;
  min-width: 0;
  color: var(--mv-search-text);
  background: transparent;
}

.search-input::placeholder {
  color: var(--mv-search-placeholder);
}

.search-button {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-icon {
  font-size: 1.1rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.theme-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--mv-ghost-border);
  border-radius: 999px;
  padding: 0.4rem 0.8rem;
  background: var(--mv-surface-muted);
  color: var(--mv-ghost-text);
  cursor: pointer;
  font-size: 0.85rem;
}

.profile-link {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  text-decoration: none;
  color: var(--mv-nav-text);
  font-size: 0.9rem;
}

.profile-icon {
  font-size: 1.2rem;
}

.auth-actions {
  display: flex;
  gap: 0.5rem;
}

.auth-btn {
  padding: 0.5rem 1rem;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.85rem;
  text-decoration: none;
  cursor: pointer;
}

.auth-btn.ghost {
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
}

.auth-btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
}

.logout-btn {
  background: transparent;
  border: 1px solid var(--mv-ghost-border);
  color: var(--mv-ghost-text);
}
</style>
