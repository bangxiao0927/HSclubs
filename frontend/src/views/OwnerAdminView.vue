<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const { currentUser, userLoading, hasCheckedSession } = storeToRefs(authStore)

const clubs = ref<Club[]>([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const categoryFilter = ref('all')
const hasLoadedOnce = ref(false)
const lastFetchedAt = ref<Date | null>(null)

const isOwner = computed(() => Boolean(currentUser.value?.isOwner))
const sessionReady = computed(() => hasCheckedSession.value || !userLoading.value)

const clubImage = (club: Club) => club.imageUrl ?? `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(club.name)}`

const loadClubs = async () => {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    clubs.value = await fetchClubs()
    hasLoadedOnce.value = true
    lastFetchedAt.value = new Date()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load clubs'
  } finally {
    loading.value = false
  }
}

watch(
  isOwner,
  (owner) => {
    if (owner && !hasLoadedOnce.value && !loading.value) {
      void loadClubs()
    }
    if (!owner) {
      clubs.value = []
      hasLoadedOnce.value = false
    }
  },
  { immediate: true }
)

const categories = computed(() => {
  const set = new Set(
    clubs.value
      .map((club) => club.category)
      .filter((category): category is string => Boolean(category?.trim()))
  )
  return Array.from(set).sort((a, b) => a.localeCompare(b))
})

const filteredClubs = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  return clubs.value.filter((club) => {
    if (categoryFilter.value !== 'all' && club.category !== categoryFilter.value) {
      return false
    }
    if (!query) {
      return true
    }
    const haystacks = [club.name, club.aliasName, club.advisor, club.contactEmail]
      .filter((value): value is string => Boolean(value))
      .map((value) => value.toLowerCase())
    return haystacks.some((value) => value.includes(query))
  })
})

const totalMembers = computed(() => clubs.value.reduce((sum, club) => sum + (club.memberCount ?? 0), 0))
const lastUpdatedLabel = computed(() => {
  if (!lastFetchedAt.value) {
    return null
  }
  return new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit' }).format(lastFetchedAt.value)
})

const emptyStateMessage = computed(() => {
  if (clubs.value.length && !filteredClubs.value.length) {
    return 'No clubs match your filters.'
  }
  return 'No clubs available yet.'
})

const resetFilters = () => {
  searchQuery.value = ''
  categoryFilter.value = 'all'
}
</script>

<template>
  <section v-if="!sessionReady" class="page-shell gate-shell">
    <div class="status-card">Checking your owner access…</div>
  </section>

  <section v-else-if="isOwner" class="owner-admin page-shell">
    <header class="owner-hero">
      <div class="hero-copy">
        <p class="section-label">Owner control</p>
        <h1>Clubs administration</h1>
        <p>
          Monitor each club's status, jump into their workspace, and keep rosters current without leaving this dashboard.
        </p>
        <div class="hero-stats">
          <div class="stat-card">
            <span>Total clubs</span>
            <strong>{{ clubs.length }}</strong>
          </div>
          <div class="stat-card">
            <span>Total members</span>
            <strong>{{ totalMembers }}</strong>
          </div>
          <div class="stat-card">
            <span>Categories</span>
            <strong>{{ categories.length }}</strong>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <p v-if="lastUpdatedLabel" class="sync-label">Last synced {{ lastUpdatedLabel }}</p>
        <button type="button" class="ghost-btn" @click="loadClubs" :disabled="loading">
          {{ loading ? 'Refreshing…' : 'Refresh data' }}
        </button>
      </div>
    </header>

    <div class="filters">
      <label>
        <span>Search</span>
        <input v-model="searchQuery" type="search" placeholder="Search by club, advisor, or contact" />
      </label>
      <label>
        <span>Category</span>
        <select v-model="categoryFilter">
          <option value="all">All categories</option>
          <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
        </select>
      </label>
      <button
        v-if="categoryFilter !== 'all' || searchQuery"
        type="button"
        class="ghost-btn"
        @click="resetFilters"
      >Clear filters</button>
    </div>

    <div v-if="loading" class="status-card">Loading clubs…</div>
    <div v-else-if="error" class="status-card error">
      <p>{{ error }}</p>
      <button type="button" class="ghost-btn" @click="loadClubs">Try again</button>
    </div>
    <div v-else>
      <div v-if="filteredClubs.length" class="club-table">
        <article v-for="club in filteredClubs" :key="club.id" class="club-row">
          <div class="club-main">
            <div class="club-avatar">
              <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
            </div>
            <div>
              <h3>{{ club.name }}</h3>
              <p class="alias" :class="{ muted: !club.aliasName }">
                {{ club.aliasName || 'No alias' }}
              </p>
            </div>
          </div>
          <div class="club-meta">
            <span>{{ club.category }}</span>
            <span>{{ club.memberCount }} members</span>
            <span>{{ club.advisor || 'Advisor TBD' }}</span>
          </div>
          <div class="row-actions">
            <RouterLink :to="`/clubs/${club.id}`" class="ghost-btn small">View</RouterLink>
            <RouterLink :to="`/clubs/${club.id}/admin`" class="primary-btn small">Manage</RouterLink>
          </div>
        </article>
      </div>
      <div v-else class="status-card muted">
        <p>{{ emptyStateMessage }}</p>
        <button
          v-if="clubs.length && (categoryFilter !== 'all' || searchQuery)"
          type="button"
          class="ghost-btn"
          @click="resetFilters"
        >Clear filters</button>
      </div>
    </div>
  </section>

  <section v-else class="page-shell gate-shell">
    <div class="gate-card">
      <p class="section-label">Restricted</p>
      <h1>Owner access required</h1>
      <p>Only the site owner can manage every club. Switch to the owner account to continue.</p>
      <div class="gate-actions">
        <RouterLink to="/" class="ghost-btn">Return home</RouterLink>
        <RouterLink to="/profile" class="primary-btn">Go to profile</RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.owner-admin {
  display: flex;
  flex-direction: column;
  gap: clamp(1.5rem, 4vw, 2.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.owner-hero {
  display: flex;
  justify-content: space-between;
  gap: clamp(1rem, 3vw, 2rem);
  border-radius: 36px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: clamp(1.5rem, 4vw, 3rem);
  background: radial-gradient(circle at top left, rgba(250, 204, 21, 0.15), rgba(5, 5, 5, 0.95));
  flex-wrap: wrap;
}

.hero-copy {
  flex: 1 1 300px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.stat-card {
  border-radius: 20px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: 0.75rem 1.1rem;
  background: rgba(10, 10, 10, 0.75);
  min-width: 140px;
}

.stat-card span {
  font-size: 0.85rem;
  color: rgba(254, 252, 232, 0.7);
}

.stat-card strong {
  display: block;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--mv-gold);
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}

.sync-label {
  margin: 0;
  color: rgba(254, 252, 232, 0.6);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: flex-end;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  color: rgba(254, 252, 232, 0.8);
}

input,
select {
  border-radius: 16px;
  border: 1px solid rgba(254, 252, 232, 0.15);
  background: rgba(12, 12, 12, 0.85);
  color: #fefce8;
  padding: 0.65rem 0.9rem;
  min-width: 220px;
}

.club-table {
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.15);
  background: rgba(7, 7, 7, 0.9);
  overflow: hidden;
}

.club-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.75rem;
  border-bottom: 1px solid rgba(254, 252, 232, 0.06);
  flex-wrap: wrap;
}

.club-row:last-of-type {
  border-bottom: none;
}

.club-main {
  display: flex;
  align-items: center;
  gap: 1rem;
  min-width: 220px;
}

.club-avatar {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.25);
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.club-main h3 {
  margin: 0;
}

.alias {
  margin: 0.2rem 0 0;
  font-size: 0.9rem;
  color: rgba(254, 252, 232, 0.75);
}

.alias.muted {
  color: rgba(254, 252, 232, 0.5);
}

.club-meta {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
  color: rgba(254, 252, 232, 0.7);
  min-width: 200px;
}

.row-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.ghost-btn,
.primary-btn {
  border-radius: 999px;
  padding: 0.5rem 1.2rem;
  font-weight: 600;
  border: 1px solid rgba(250, 204, 21, 0.35);
  background: transparent;
  color: rgba(254, 252, 232, 0.85);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
  text-decoration: none;
  text-align: center;
}

.ghost-btn:disabled,
.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.primary-btn {
  background: var(--mv-gold);
  color: #111;
  border-color: var(--mv-gold);
}

.ghost-btn.small,
.primary-btn.small {
  padding: 0.4rem 1rem;
}

.status-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  padding: 1rem 1.25rem;
  background: rgba(7, 7, 7, 0.9);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: #fecaca;
}

.status-card.muted {
  color: rgba(254, 252, 232, 0.75);
}

.gate-shell {
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.gate-card {
  border-radius: 32px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: clamp(1.75rem, 4vw, 3rem);
  background: rgba(10, 10, 10, 0.9);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: flex-start;
}

.gate-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

@media (max-width: 700px) {
  .club-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-actions {
    width: 100%;
    align-items: flex-start;
  }

  .filters label {
    width: 100%;
  }

  input,
  select {
    width: 100%;
    min-width: 0;
  }
}
</style>
