<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const searchQuery = ref('')
const route = useRoute()
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
            >Explore</RouterLink>
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
