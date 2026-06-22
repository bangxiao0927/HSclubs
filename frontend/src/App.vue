<script setup lang="ts">
import { computed, onErrorCaptured, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'
import { useSchoolStore } from './stores/school'
import ErrorDisplay from './components/ErrorDisplay.vue'

const searchQuery = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const schoolStore = useSchoolStore()
const { isAuthenticated, currentUser } = storeToRefs(authStore)
const { currentSchool, currentSchoolSlug } = storeToRefs(schoolStore)

const mobileMenuOpen = ref(false)

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}

// Close the mobile menu whenever the route changes
watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
  },
)

const schoolSlug = computed(() => {
  const slug = route.params.schoolSlug
  return typeof slug === 'string' ? slug : ''
})

const isSchoolRoute = computed(() => Boolean(schoolSlug.value))
const activeSchool = computed(() => (isSchoolRoute.value ? currentSchool.value : null))
const logoText = computed(
  () => activeSchool.value?.shortName || activeSchool.value?.schoolName || 'HS Clubs',
)
const searchPlaceholder = computed(() =>
  isSchoolRoute.value ? 'Search your favorite clubs' : 'Search schools and club directories',
)

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

const handleMobileLogout = () => {
  closeMobileMenu()
  handleLogout()
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

const appError = ref<string | null>(null)

const handleAppError = (err: unknown) => {
  console.error('Global error:', err)
  appError.value = err instanceof Error ? err.message : 'An unexpected error occurred'
}

const resetError = () => {
  appError.value = null
}

onErrorCaptured((err) => {
  handleAppError(err)
  return false // prevent error propagation
})

const submitSearch = () => {
  const q = searchQuery.value.trim()
  closeMobileMenu()
  if (schoolSlug.value) {
    void router.push({
      name: q ? 'school-club-search' : 'school-home',
      params: { schoolSlug: schoolSlug.value },
      query: q ? { q } : {},
    })
  } else {
    void router.push({
      name: q ? 'school-picker' : 'home',
      query: q ? { q } : {},
    })
  }
}

watch(isAuthenticated, (authenticated, previous) => {
  if (authenticated && !previous) {
    void authStore.refreshUser()
  }
})

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
            <RouterLink to="/" class="logo-link">
              <img class="logo-icon" src="/android-chrome-512x512.png" alt="HS Clubs logo" />
              <span class="logo-text">{{ logoText }}</span>
            </RouterLink>
          </div>
          <nav class="nav">
            <RouterLink
              :to="navHome"
              class="nav-link"
              :class="{ active: route.name === 'home' || route.name === 'school-home' }"
              >Home</RouterLink
            >
            <RouterLink
              v-if="!isSchoolRoute"
              to="/schools"
              class="nav-link"
              :class="{ active: route.name === 'school-picker' }"
              >Schools</RouterLink
            >
            <RouterLink
              v-if="isSchoolRoute"
              :to="navCategories"
              class="nav-link"
              :class="{ active: route.name === 'about' || route.name === 'school-about' }"
              >Category</RouterLink
            >
            <RouterLink
              v-if="isSchoolRoute"
              :to="navCalendar"
              class="nav-link"
              :class="{ active: route.name === 'calendar' || route.name === 'school-calendar' }"
              >Calendar</RouterLink
            >
            <RouterLink
              :to="navAdmin"
              v-if="currentUser?.isOwner"
              class="nav-link"
              :class="{
                active: route.name === 'owner-clubs' || route.name === 'school-owner-clubs',
              }"
              >Admin</RouterLink
            >
            <RouterLink
              v-if="currentUser?.isOwner"
              to="/platform/admin"
              class="nav-link"
              :class="{ active: route.name === 'platform-admin' }"
              >Platform</RouterLink
            >
          </nav>
        </div>

        <form class="search-bar" @submit.prevent="submitSearch">
          <input
            v-model="searchQuery"
            type="search"
            :placeholder="searchPlaceholder"
            class="search-input"
          />
          <button type="submit" class="search-button" aria-label="Search clubs">
            <span class="search-icon">🔍</span>
          </button>
        </form>

        <button
          type="button"
          class="mobile-menu-toggle"
          :class="{ open: mobileMenuOpen }"
          :aria-expanded="mobileMenuOpen"
          aria-label="Toggle menu"
          @click="toggleMobileMenu"
        >
          <span class="hamburger"></span>
        </button>

        <div class="header-right">
          <RouterLink v-if="schoolSlug" to="/schools" class="nav-link school-switch"
            >Change school</RouterLink
          >
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
          <button v-else type="button" class="auth-btn ghost logout-btn" @click="handleLogout">
            Log out
          </button>
        </div>
      </div>

      <Transition name="mobile-menu">
        <div v-if="mobileMenuOpen" class="mobile-menu">
          <form class="search-bar" @submit.prevent="submitSearch">
            <input
              v-model="searchQuery"
              type="search"
              :placeholder="searchPlaceholder"
              class="search-input"
            />
            <button type="submit" class="search-button" aria-label="Search clubs">
              <span class="search-icon">🔍</span>
            </button>
          </form>
          <nav class="mobile-nav">
            <RouterLink :to="navHome" class="mobile-nav-link" @click="closeMobileMenu"
              >Home</RouterLink
            >
            <RouterLink
              v-if="!isSchoolRoute"
              to="/schools"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Schools</RouterLink
            >
            <RouterLink
              v-if="isSchoolRoute"
              :to="navCategories"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Category</RouterLink
            >
            <RouterLink
              v-if="isSchoolRoute"
              :to="navCalendar"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Calendar</RouterLink
            >
            <RouterLink
              v-if="currentUser?.isOwner"
              :to="navAdmin"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Admin</RouterLink
            >
            <RouterLink
              v-if="currentUser?.isOwner"
              to="/platform/admin"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Platform</RouterLink
            >
          </nav>
          <div class="mobile-actions">
            <RouterLink
              v-if="schoolSlug"
              to="/schools"
              class="mobile-nav-link"
              @click="closeMobileMenu"
              >Change school</RouterLink
            >
            <button type="button" class="mobile-nav-link" @click="toggleTheme">
              {{ themeLabel }}
            </button>
            <template v-if="isAuthenticated">
              <RouterLink :to="navProfile" class="mobile-nav-link" @click="closeMobileMenu"
                >Profile</RouterLink
              >
              <button type="button" class="mobile-nav-link" @click="handleMobileLogout">
                Log out
              </button>
            </template>
            <template v-else>
              <RouterLink to="/auth?intent=login" class="mobile-nav-link" @click="closeMobileMenu"
                >Log in</RouterLink
              >
              <RouterLink
                to="/auth?intent=register"
                class="auth-btn primary"
                @click="closeMobileMenu"
                >Register</RouterLink
              >
            </template>
          </div>
        </div>
      </Transition>
    </header>

    <main class="view-container">
      <ErrorDisplay v-if="appError" :message="appError" @retry="resetError" />
      <RouterView v-else />
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

/* ---- Mobile menu toggle (hamburger) ---- */
.mobile-menu-toggle {
  display: none;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--mv-ghost-border);
  border-radius: 12px;
  background: var(--mv-surface-muted);
  cursor: pointer;
  flex-shrink: 0;
  gap: 4px;
}

.hamburger,
.hamburger::before,
.hamburger::after {
  display: block;
  width: 20px;
  height: 2px;
  border-radius: 2px;
  background: var(--mv-ghost-text);
  transition:
    transform 0.25s ease,
    opacity 0.2s ease;
}

.hamburger::before,
.hamburger::after {
  content: '';
}

.mobile-menu-toggle.open .hamburger {
  background: transparent;
}

.mobile-menu-toggle.open .hamburger::before {
  transform: translateY(6px) rotate(45deg);
}

.mobile-menu-toggle.open .hamburger::after {
  transform: translateY(-6px) rotate(-45deg);
}

/* ---- Mobile dropdown menu ---- */
.mobile-menu {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem var(--page-padding-inline) 1.25rem;
  border-top: 1px solid var(--mv-header-border);
  background: var(--mv-header-bg);
  backdrop-filter: blur(12px);
  overflow: hidden;
}

.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.mobile-nav-link {
  display: block;
  padding: 0.7rem 0.5rem;
  border-radius: 12px;
  color: var(--mv-nav-text);
  text-decoration: none;
  font-size: 1rem;
  font-weight: 500;
  background: transparent;
  border: none;
  cursor: pointer;
  width: 100%;
  text-align: left;
}

.mobile-nav-link:active,
.mobile-nav-link:hover {
  background: var(--mv-surface-accent);
  color: var(--mv-nav-text-active);
}

.mobile-actions {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--mv-header-border);
}

.mobile-actions .auth-btn.primary {
  text-align: center;
  padding: 0.7rem 1rem;
}

.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition:
    max-height 0.3s ease,
    opacity 0.25s ease;
  max-height: 500px;
}

.mobile-menu-enter-from,
.mobile-menu-leave-to {
  max-height: 0;
  opacity: 0;
}

/* ---- Responsive header breakpoints ---- */
@media (max-width: 1024px) {
  .header-inner {
    gap: 0.85rem;
  }

  .nav {
    gap: 1rem;
  }

  .search-bar {
    max-width: 280px;
  }
}

@media (max-width: 860px) {
  .header-inner {
    flex-wrap: wrap;
  }

  .header-left {
    gap: 1rem;
  }

  .search-bar {
    order: 3;
    flex: 1 1 100%;
    max-width: 100%;
  }
}

@media (max-width: 720px) {
  .header {
    padding-block: 0.65rem;
  }

  .header-inner {
    flex-wrap: nowrap;
  }

  .nav,
  .header-right,
  .search-bar {
    display: none;
  }

  .mobile-menu-toggle {
    display: flex;
  }

  .mobile-menu .search-bar {
    display: flex;
    order: 0;
  }
}

@media (max-width: 480px) {
  .logo-text {
    font-size: 0.95rem;
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .logo-icon {
    width: 36px;
    height: 36px;
  }
}
</style>
