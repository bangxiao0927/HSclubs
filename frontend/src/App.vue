<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from './stores/auth'

const searchQuery = ref('')
const route = useRoute()
const authStore = useAuthStore()
const { isAuthenticated } = storeToRefs(authStore)

const handleLogout = () => {
  authStore.logout()
}
</script>

<template>
  <div class="app-shell">
    <header class="header">
      <div class="header-inner page-shell">
        <div class="header-left">
          <div class="logo">
            <div class="logo-icon">🎓</div>
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
          </nav>
        </div>

        <div class="search-bar">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search your favorite clubs"
            class="search-input"
          />
          <span class="search-icon">🔍</span>
        </div>

        <div class="header-right">
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
  background: rgba(5, 5, 5, 0.9);
  border-bottom: 1px solid rgba(250, 204, 21, 0.15);
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(12px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
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
  background: #fde047;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: #111111;
}

.logo-text {
  font-weight: 700;
  color: #fde047;
}

.nav {
  display: flex;
  align-items: center;
  gap: clamp(1rem, 2vw, 2rem);
  font-size: 0.95rem;
  flex-wrap: wrap;
}

.nav-link {
  color: rgba(254, 252, 232, 0.75);
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link.active {
  color: #fefce8;
  font-weight: 600;
}

.nav-link:hover {
  color: #fde047;
}

.search-bar {
  display: flex;
  align-items: center;
  background: rgba(15, 15, 15, 0.85);
  border-radius: 999px;
  padding: 0.6rem 1.5rem;
  flex: 1 1 260px;
  min-width: 220px;
  max-width: 440px;
  gap: 0.5rem;
  border: 1px solid rgba(250, 204, 21, 0.35);
}

.search-input {
  border: none;
  outline: none;
  flex: 1;
  font-size: 0.95rem;
  color: #fefce8;
  background: transparent;
}

.search-input::placeholder {
  color: rgba(254, 252, 232, 0.4);
}

.search-icon {
  color: rgba(254, 252, 232, 0.65);
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
  border: 1px solid rgba(254, 252, 232, 0.25);
  text-decoration: none;
  color: rgba(254, 252, 232, 0.85);
  transition: background 0.2s, border-color 0.2s;
}

.profile-link:hover {
  border-color: rgba(253, 224, 71, 0.8);
  background: rgba(253, 224, 71, 0.08);
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
  border: 1px solid rgba(254, 252, 232, 0.4);
  color: rgba(254, 252, 232, 0.9);
}

.auth-btn.primary {
  background: #fde047;
  color: #111;
  box-shadow: 0 10px 20px rgba(253, 224, 71, 0.35);
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
  .nav {
    width: 100%;
    justify-content: space-between;
  }

  .search-bar {
    max-width: none;
    width: 100%;
  }
}
</style>
