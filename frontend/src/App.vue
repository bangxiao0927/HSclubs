<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onErrorCaptured, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'
import ErrorDisplay from './components/ErrorDisplay.vue'
import { schoolTemplate } from './config/schoolTemplate'
import { useTheme } from './composables/useTheme'

const searchQuery = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { isAuthenticated, currentUser } = storeToRefs(authStore)

const mobileMenuOpen = ref(false)
const mobileMenuSheet = ref<HTMLElement | null>(null)
const mobileUserCenterButton = ref<HTMLButtonElement | null>(null)
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

const closeMobileMenuAndRestoreFocus = () => {
  closeMobileMenu()
  void nextTick(() => mobileUserCenterButton.value?.focus())
}

// Move focus into the sheet when it opens. The Escape handler lives on the sheet, so it only
// sees keydown events that bubble up from inside it -- without this, a keyboard user's focus
// stays on the trigger and Escape never reaches the sheet.
watch(
  () => mobileMenuOpen.value,
  (open) => {
    if (open) mobileMenuSheet.value?.focus()
  },
  { flush: 'post' },
)

// Close the mobile menu whenever the route changes
watch(
  () => route.fullPath,
  () => {
    mobileMenuOpen.value = false
  },
)

const logoText = schoolTemplate.shortName
// A bound constant (rather than a literal in the template) so Vue's asset-url
// transform, which only rewrites relative paths, leaves this root-absolute
// public path untouched under both Vite build and Vitest's SSR module runner.
const logoUrl = '/android-chrome-512x512.png'
const fullSearchPlaceholder = 'Search clubs, advisors, categories, or keywords'
// The mobile title bar keeps the brand and the search field on one row, so the
// long placeholder would be clipped mid-word there; a short one is used instead.
const compactViewport = ref(false)
const compactViewportQuery =
  typeof window.matchMedia === 'function' ? window.matchMedia('(max-width: 720px)') : null
if (compactViewportQuery) {
  compactViewport.value = compactViewportQuery.matches
  const updateCompactViewport = (event: MediaQueryListEvent) => {
    compactViewport.value = event.matches
  }
  compactViewportQuery.addEventListener('change', updateCompactViewport)
  onBeforeUnmount(() => compactViewportQuery.removeEventListener('change', updateCompactViewport))
}
const searchPlaceholder = computed(() =>
  compactViewport.value ? 'Search clubs' : fullSearchPlaceholder,
)
document.title = logoText

const handleLogout = () => {
  authStore.logout()
}

// Left open after toggling so the visual change is immediately visible,
// unlike navigation actions in this menu which close it.
const handleMobileThemeToggle = () => {
  toggleTheme()
}

const { theme, themeLabel, toggleTheme } = useTheme()

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

// The native clear button on an <input type="search"> emits `search` without
// submitting its form. Keep the URL/results in sync instead of leaving stale
// results visible behind an empty title-bar field.
const handleNativeSearch = () => {
  if (!searchQuery.value.trim() && route.name === 'club-search') {
    submitSearch()
  }
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
            @search="handleNativeSearch"
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
    </header>

    <nav class="mobile-tab-bar" aria-label="Mobile navigation">
      <RouterLink to="/" class="mobile-tab" :class="{ active: route.name === 'home' }">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m3 10 9-7 9 7v10h-6v-6H9v6H3V10Z" />
        </svg>
        <span>Home</span>
      </RouterLink>
      <RouterLink to="/about" class="mobile-tab" :class="{ active: route.name === 'about' }">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z" />
        </svg>
        <span>Category</span>
      </RouterLink>
      <RouterLink to="/calendar" class="mobile-tab" :class="{ active: route.name === 'calendar' }">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M7 2h2v2h6V2h2v2h3v18H4V4h3V2Zm11 8H6v10h12V10Z" />
        </svg>
        <span>Calendar</span>
      </RouterLink>
      <!-- Signed in, the account tab is a plain link to the profile page, which already holds
           everything the sheet used to offer (theme, admin, sign out). The sheet stays for
           signed-out visitors, who have no profile page to send them to. -->
      <RouterLink
        v-if="isAuthenticated"
        to="/profile"
        class="mobile-tab mobile-user-center"
        :class="{ active: route.name === 'profile' }"
      >
        <img
          v-if="profileAvatarUrl"
          class="profile-avatar"
          :src="profileAvatarUrl"
          alt=""
          referrerpolicy="no-referrer"
          @error="handleProfileAvatarError"
        />
        <span v-else class="profile-icon" aria-hidden="true">{{ profileInitial }}</span>
        <span>Account</span>
      </RouterLink>
      <button
        v-else
        ref="mobileUserCenterButton"
        type="button"
        class="mobile-tab mobile-user-center"
        :class="{ active: mobileMenuOpen }"
        :aria-expanded="mobileMenuOpen"
        aria-controls="mobile-navigation"
        @click="toggleMobileMenu"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z"
          />
        </svg>
        <span>Sign in</span>
      </button>
    </nav>

    <Transition name="mobile-menu">
      <div
        ref="mobileMenuSheet"
        v-if="mobileMenuOpen && !isAuthenticated"
        id="mobile-navigation"
        class="mobile-menu"
        tabindex="-1"
        @keydown.esc.stop.prevent="closeMobileMenuAndRestoreFocus"
      >
        <div class="mobile-menu-inner">
          <div class="mobile-menu-handle" aria-hidden="true"></div>
          <h2 class="mobile-menu-title">Not signed in</h2>
          <p class="mobile-menu-subtitle">
            Sign in to join clubs and save your interests.
          </p>
          <button type="button" class="mobile-theme-toggle" @click="handleMobileThemeToggle">
            <span class="theme-icon" aria-hidden="true">{{ theme === 'light' ? '🌙' : '☀️' }}</span>
            <span>{{ themeLabel }}</span>
          </button>
          <div class="mobile-actions">
            <RouterLink to="/auth?intent=login" class="mobile-nav-link" @click="closeMobileMenu"
              >Log in</RouterLink
            >
            <RouterLink to="/auth?intent=register" class="auth-btn primary" @click="closeMobileMenu"
              >Register</RouterLink
            >
          </div>
        </div>
      </div>
    </Transition>

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
  white-space: nowrap;
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
.mobile-tab-bar {
  display: none;
}

.mobile-menu {
  display: none;
  opacity: 1;
}

.mobile-menu-inner {
  overflow-y: auto;
  min-height: 0;
  padding: 1rem var(--page-padding-inline) 1.5rem;
}

.mobile-menu-handle {
  width: 2.5rem;
  height: 0.3rem;
  margin: 0 auto 0.75rem;
  border-radius: 999px;
  background: var(--mv-glass-highlight);
}

.mobile-menu-title {
  margin: 0;
  color: var(--mv-text);
  font-size: 1.1rem;
}

.mobile-menu-subtitle {
  margin: 0.2rem 0 0.9rem;
  color: var(--mv-text-muted);
  font-size: 0.85rem;
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
  border-radius: 14px;
  border: 1px solid var(--mv-glass-border);
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
  border-top: 1px solid var(--mv-glass-border);
}

.mobile-actions .auth-btn.primary {
  text-align: center;
  padding: 0.7rem 1rem;
}

.mobile-menu-enter-active,
.mobile-menu-leave-active {
  transition:
    transform 0.3s ease,
    opacity 0.25s ease;
}

.mobile-menu-enter-from,
.mobile-menu-leave-to {
  transform: translateY(calc(100% + 5rem));
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
  .app-shell {
    padding-bottom: calc(5.6rem + env(safe-area-inset-bottom));
  }

  .header {
    padding-block: 0.65rem;
  }

  .header-inner {
    flex-wrap: nowrap;
    gap: 0.5rem;
  }

  .header-left,
  .logo {
    min-width: 0;
  }

  /* The school's short name is the title bar's identity, so it keeps its full
     width on every phone: the search field beside it shrinks instead. */
  .logo-link,
  .logo-text {
    flex-shrink: 0;
  }

  .nav,
  .header-right {
    display: none;
  }

  /* Search shares the single title-bar row with the brand: it shrinks instead
     of wrapping, so the header stays one line tall. */
  .search-bar {
    display: flex;
    order: 0;
    flex: 1 1 auto;
    min-width: 0;
    max-width: none;
    padding: 0.32rem 0.4rem 0.32rem 0.75rem;
  }

  .search-input {
    /* iOS Safari zooms the viewport when focusing inputs below 16px. */
    font-size: 1rem;
  }

  .mobile-tab-bar {
    position: fixed;
    right: 0.75rem;
    bottom: calc(0.6rem + env(safe-area-inset-bottom));
    left: 0.75rem;
    z-index: 20;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0.25rem;
    padding: 0.35rem;
    border: 1px solid var(--mv-glass-border);
    /* Continuous, capsule-style corners like the iOS 26 floating tab bar:
       the radius tracks half the bar height instead of a fixed small value. */
    border-radius: 999px;
    background: var(--mv-glass-bg);
    box-shadow:
      var(--mv-glass-shadow),
      inset 0 1px 0 var(--mv-glass-highlight);
    backdrop-filter: var(--mv-glass-blur);
    -webkit-backdrop-filter: var(--mv-glass-blur);
  }

  .mobile-tab {
    display: flex;
    min-width: 0;
    min-height: 3.5rem;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    gap: 0.2rem;
    padding: 0.3rem 0.15rem;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: var(--mv-nav-text);
    font: inherit;
    font-size: 0.68rem;
    cursor: pointer;
    transition:
      background 0.2s ease,
      color 0.2s ease;
  }

  .mobile-tab svg {
    width: 1.35rem;
    height: 1.35rem;
    fill: currentColor;
  }

  .mobile-tab .profile-avatar,
  .mobile-tab .profile-icon {
    width: 1.35rem;
    height: 1.35rem;
    flex: 0 0 1.35rem;
  }

  .mobile-tab.active,
  .mobile-tab:active {
    background: var(--mv-glass-active);
    color: var(--mv-nav-text-active);
    box-shadow: inset 0 1px 0 var(--mv-glass-highlight);
  }

  .mobile-menu {
    position: fixed;
    right: 0.75rem;
    bottom: calc(5.1rem + env(safe-area-inset-bottom));
    left: 0.75rem;
    z-index: 19;
    display: flex;
    flex-direction: column;
    max-height: min(70vh, 32rem);
    overflow: hidden;
    border: 1px solid var(--mv-glass-border);
    /* Sheet corners stay squircle-like (large but not a full capsule), matching
       the iOS 26 grouped-sheet radius. */
    border-radius: 34px;
    background: var(--mv-glass-bg);
    box-shadow:
      var(--mv-glass-shadow),
      inset 0 1px 0 var(--mv-glass-highlight);
    backdrop-filter: var(--mv-glass-blur);
    -webkit-backdrop-filter: var(--mv-glass-blur);
  }
}

@media (max-width: 480px) {
  .logo-text {
    font-size: 0.9rem;
    /* Not a truncation of the school's name (MVHS-length names are far below
       this): only a guard so an unusually long configured brand cannot push the
       search field off the row. */
    max-width: 45vw;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .logo-icon {
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
  }

  .logo-link {
    gap: 0.45rem;
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
  .mobile-menu-leave-active {
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
