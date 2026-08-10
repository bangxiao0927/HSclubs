<script setup lang="ts">
import { computed, onErrorCaptured, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'
import ErrorDisplay from './components/ErrorDisplay.vue'
import { schoolTemplate, type ColorMode } from './config/schoolTemplate'
import { resolveInitialTheme } from './utils/themeBootstrap'

const searchQuery = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isAuthenticated, currentUser } = storeToRefs(authStore)

const mobileMenuOpen = ref(false)
const profileAvatarFailed = ref(false)

const profileInitial = computed(() => {
  const seed = currentUser.value?.displayName?.trim() || currentUser.value?.email?.trim() || 'U'
  return seed.charAt(0).toUpperCase()
})

const profileAvatarUrl = computed(() => {
  if (profileAvatarFailed.value) return ''
  return currentUser.value?.avatarUrl?.trim() || ''
})

const handleProfileAvatarError = () => {
  profileAvatarFailed.value = true
}

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

const logoText = schoolTemplate.brandName
// A bound constant (rather than a literal in the template) so Vue's asset-url
// transform, which only rewrites relative paths, leaves this root-absolute
// public path untouched under both Vite build and Vitest's SSR module runner.
const logoUrl = '/android-chrome-512x512.png'
const searchPlaceholder = 'Search clubs, advisors, categories, or keywords'
document.title = logoText

const handleLogout = () => {
  authStore.logout()
}

const handleMobileLogout = () => {
  closeMobileMenu()
  handleLogout()
}

// Left open after toggling so the visual change is immediately visible,
// unlike navigation actions in this menu which close it.
const handleMobileThemeToggle = () => {
  toggleTheme()
}

const theme = ref<ColorMode>(schoolTemplate.defaultColorMode)
const themeLabel = computed(() => (theme.value === 'light' ? 'Dark mode' : 'Light mode'))

const applyTheme = (value: ColorMode) => {
  document.documentElement.dataset.theme = value
  document.documentElement.style.colorScheme = value
}

const persistTheme = (value: ColorMode) => {
  try {
    window.localStorage.setItem('theme', value)
  } catch (error) {
    console.warn('Failed to persist theme preference.', error)
  }
}

const toggleTheme = () => {
  const nextTheme = theme.value === 'light' ? 'dark' : 'light'
  theme.value = nextTheme
  persistTheme(nextTheme)
}

try {
  theme.value = resolveInitialTheme(
    window.localStorage.getItem('theme'),
    schoolTemplate.defaultColorMode,
  )
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
  void router.push({
    name: q ? 'club-search' : 'home',
    query: q ? { q } : {},
  })
}

watch(isAuthenticated, (authenticated, previous) => {
  if (authenticated && !previous) {
    void authStore.refreshUser()
  }
  if (!authenticated && previous && route.matched.some((record) => record.meta.requiresAuth)) {
    void router.replace({
      name: 'auth-choice',
      query: {
        intent: 'login',
        redirect: route.fullPath,
      },
    })
  }
})

watch(
  () => currentUser.value?.avatarUrl,
  () => {
    profileAvatarFailed.value = false
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
            <RouterLink to="/" class="logo-link">
              <img class="logo-icon" :src="logoUrl" :alt="`${logoText} logo`" />
              <span class="logo-text">{{ logoText }}</span>
            </RouterLink>
          </div>
          <nav class="nav">
            <RouterLink to="/" class="nav-link" :class="{ active: route.name === 'home' }"
              >Home</RouterLink
            >
            <RouterLink to="/about" class="nav-link" :class="{ active: route.name === 'about' }"
              >Category</RouterLink
            >
            <RouterLink
              to="/calendar"
              class="nav-link"
              :class="{ active: route.name === 'calendar' }"
              >Calendar</RouterLink
            >
            <RouterLink
              to="/admin"
              v-if="currentUser?.isOwner"
              class="nav-link"
              :class="{ active: route.name === 'owner-clubs' }"
              >Admin</RouterLink
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
          aria-controls="mobile-navigation"
          aria-label="Toggle menu"
          @click="toggleMobileMenu"
        >
          <span class="hamburger"></span>
        </button>

        <div class="header-right">
          <button type="button" class="theme-toggle" @click="toggleTheme">
            <span class="theme-icon" aria-hidden="true">{{ theme === 'light' ? '🌙' : '☀️' }}</span>
            <span>{{ themeLabel }}</span>
          </button>
          <RouterLink v-if="isAuthenticated" to="/profile" class="profile-link">
            <img
              v-if="profileAvatarUrl"
              class="profile-avatar"
              :src="profileAvatarUrl"
              :alt="`${currentUser?.displayName || 'Profile'} avatar`"
              referrerpolicy="no-referrer"
              @error="handleProfileAvatarError"
            />
            <span v-else class="profile-icon">{{ profileInitial }}</span>
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
        <div v-if="mobileMenuOpen" id="mobile-navigation" class="mobile-menu">
          <div class="mobile-menu-inner">
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
              <RouterLink to="/" class="mobile-nav-link" @click="closeMobileMenu">Home</RouterLink>
              <RouterLink to="/about" class="mobile-nav-link" @click="closeMobileMenu"
                >Category</RouterLink
              >
              <RouterLink to="/calendar" class="mobile-nav-link" @click="closeMobileMenu"
                >Calendar</RouterLink
              >
              <RouterLink
                v-if="currentUser?.isOwner"
                to="/admin"
                class="mobile-nav-link"
                @click="closeMobileMenu"
                >Admin</RouterLink
              >
            </nav>
            <button type="button" class="mobile-theme-toggle" @click="handleMobileThemeToggle">
              <span class="theme-icon" aria-hidden="true">{{
                theme === 'light' ? '🌙' : '☀️'
              }}</span>
              <span>{{ themeLabel }}</span>
            </button>
            <div class="mobile-actions">
              <template v-if="isAuthenticated">
                <RouterLink to="/profile" class="mobile-nav-link" @click="closeMobileMenu"
                  >Profile</RouterLink
                >
                <button type="button" class="auth-btn ghost" @click="handleMobileLogout">
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
        </div>
      </Transition>
    </header>

    <main class="view-container">
      <ErrorDisplay v-if="appError" :message="appError" @retry="resetError" />
      <RouterView v-else />
    </main>

    <footer class="app-footer">
      <div class="footer-inner page-shell">
        <span class="footer-brand">{{ logoText }}</span>
        <nav class="footer-links">
          <RouterLink to="/terms">Terms of Use</RouterLink>
          <RouterLink to="/privacy">Privacy Policy</RouterLink>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--mv-night);
  background-image: var(--app-body-bg);
  background-repeat: no-repeat;
  background-size: 100% 100%;
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
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  color: var(--mv-search-icon);
  padding: 0.2rem;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.profile-link {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--mv-profile-border);
  color: var(--mv-profile-text);
  text-decoration: none;
  font-size: 0.9rem;
  transition:
    background 0.2s,
    border-color 0.2s;
}

.profile-link:hover {
  background: var(--mv-profile-hover-bg);
  border-color: var(--mv-profile-hover-border);
}

.profile-avatar,
.profile-icon {
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 50%;
  flex: 0 0 1.5rem;
}

.profile-avatar {
  object-fit: cover;
  border: 1px solid var(--mv-profile-border);
}

.profile-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--mv-surface-soft);
  border: 1px solid var(--mv-profile-border);
  font-size: 0.8rem;
  font-weight: 700;
}

.theme-toggle {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
  cursor: pointer;
  font-size: 0.9rem;
}

.auth-actions {
  display: flex;
  gap: 0.5rem;
}

.auth-btn {
  padding: 0.4rem 1rem;
  border-radius: 999px;
  font-size: 0.9rem;
  cursor: pointer;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.auth-btn:hover {
  background: var(--mv-surface-accent);
}

.auth-btn.primary {
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  border-color: transparent;
  box-shadow: var(--mv-primary-shadow);
}

.logout-btn {
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
}

.view-container {
  flex: 1;
  padding-block: clamp(1.5rem, 3vw, 3rem);
}

/* Mobile */
.mobile-menu-toggle {
  display: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
}

.hamburger {
  display: block;
  width: 24px;
  height: 2px;
  background: var(--mv-text);
  position: relative;
  transition: background 0.2s;
}

.hamburger::before,
.hamburger::after {
  content: '';
  display: block;
  width: 24px;
  height: 2px;
  background: var(--mv-text);
  position: absolute;
  left: 0;
  transition: transform 0.25s;
}

.hamburger::before {
  top: -7px;
}
.hamburger::after {
  top: 7px;
}

.mobile-menu {
  border-bottom: 1px solid var(--mv-header-border);
  background: var(--mv-header-bg);
  display: grid;
  grid-template-rows: 1fr;
  opacity: 1;
}

.mobile-menu-inner {
  overflow: hidden;
  min-height: 0;
  padding: 1rem var(--page-padding-inline) 1.5rem;
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

.mobile-theme-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  margin-top: 0.5rem;
  padding: 0.7rem 0.5rem;
  border-radius: 12px;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
  font-size: 1rem;
  cursor: pointer;
  text-align: left;
}

.mobile-theme-toggle:hover,
.mobile-theme-toggle:active {
  background: var(--mv-surface-accent);
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
    grid-template-rows 0.3s ease,
    opacity 0.25s ease;
}

.mobile-menu-enter-from,
.mobile-menu-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}

@media (max-width: 1024px) {
  .header-inner {
    gap: 0.85rem;
  }

  .nav {
    gap: 1rem;
  }

  .search-bar {
    flex: 1 1 180px;
    min-width: 0;
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
/* ---- Footer ---- */
.app-footer {
  padding-block: 1.5rem;
  border-top: 1px solid var(--mv-header-border);
  background: var(--mv-header-bg);
  margin-top: auto;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.footer-brand {
  font-size: 0.85rem;
  color: var(--mv-text-faint);
}

.footer-links {
  display: flex;
  gap: 1.5rem;
}

.footer-links a {
  font-size: 0.85rem;
  color: var(--mv-text-faint);
  text-decoration: none;
}

.footer-links a:hover {
  color: var(--mv-nav-text-hover);
}

@media (prefers-reduced-motion: reduce) {
  .mobile-menu-enter-active,
  .mobile-menu-leave-active,
  .hamburger,
  .hamburger::before,
  .hamburger::after {
    transition: none;
  }
}

@media (max-width: 480px) {
  .footer-inner {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
