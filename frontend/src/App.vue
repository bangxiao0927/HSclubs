<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'

const searchQuery = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isAuthenticated, currentUser } = storeToRefs(authStore)

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
  void router.push({
    name: q ? 'club-search' : 'home',
    query: q ? { q } : {},
  })
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
            <img class="logo-icon" src="/android-chrome-512x512.png" alt="MVHS Clubs logo" />
            <span class="logo-text">MV Clubs</span>
          </div>
          <nav class="nav">
            <RouterLink
              to="/"
              class="nav-link"
              :class="{ active: route.name === 'home' }"
            >Home</RouterLink>
            <RouterLink
              to="/about"
              class="nav-link"
              :class="{ active: route.name === 'about' }"
            >Category</RouterLink>
            <RouterLink
              to="/calendar"
              class="nav-link"
              :class="{ active: route.name === 'calendar' }"
            >Calendar</RouterLink>
            <RouterLink
              v-if="currentUser?.isOwner"
              to="/admin"
              class="nav-link"
              :class="{ active: route.name === 'owner-clubs' }"
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
          <button type="button" class="theme-toggle" @click="toggleTheme">
            <span class="theme-icon" aria-hidden="true">{{ theme === 'light' ? '🌙' : '☀️' }}</span>
            <span>{{ themeLabel }}</span>
          </button>
          <RouterLink v-if="isAuthenticated" to="/profile" class="profile-link">
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

.logo {
  display: flex;
  align-items: center;
  gap: 0.75rem;
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
  background: transparent;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 999px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.search-icon {
  color: var(--mv-search-icon);
  font-size: 1.2rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.profile-link {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 1rem;
  border-radius: 999px;
  border: 1px solid var(--mv-profile-border);
  text-decoration: none;
  color: var(--mv-profile-text);
  transition: background 0.2s, border-color 0.2s;
}

.profile-link:hover {
  border-color: var(--mv-profile-hover-border);
  background: var(--mv-profile-hover-bg);
}

.profile-icon {
  font-size: 1.1rem;
}

.auth-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.auth-btn {
  padding: 0.45rem 1.15rem;
  border-radius: 999px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
  transition: transform 0.2s, box-shadow 0.2s;
}

.auth-btn.ghost {
  border: 1px solid var(--mv-ghost-border);
  color: var(--mv-ghost-text);
}

.auth-btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
}

.theme-toggle {
  border: 1px solid var(--mv-ghost-border);
  color: var(--mv-ghost-text);
  background: transparent;
  border-radius: 999px;
  padding: 0.4rem 0.9rem;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.theme-toggle:hover {
  transform: translateY(-1px);
  box-shadow: var(--mv-shadow-card);
}

.theme-icon {
  font-size: 1rem;
}

.auth-btn:hover {
  transform: translateY(-1px);
}

.view-container {
  flex: 1;
  width: 100%;
}

@media (max-width: 1024px) {
  .header-inner {
    flex-direction: column;
    align-items: stretch;
  }

  .header-left {
    justify-content: space-between;
  }
}

@media (max-width: 640px) {
  .header {
    padding-block: 0.75rem;
  }

  .header-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.85rem;
  }

  .nav {
    width: 100%;
    justify-content: space-between;
  }

  .search-bar {
    max-width: none;
    width: 100%;
    min-width: 0;
    padding: 0.32rem 0.45rem 0.32rem 0.9rem;
  }

  .search-input {
    font-size: 16px;
  }

  .search-button {
    width: 2rem;
    height: 2rem;
  }
}

@media (pointer: coarse) {
  .search-input {
    font-size: 16px;
  }
}
</style>
