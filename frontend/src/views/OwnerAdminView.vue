<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { RouterLink } from 'vue-router'
import { fetchClubCount, fetchAllClubs, createClub } from '../services/clubService'
import type { Club } from '../types/club'
import { useAuthStore } from '../stores/auth'
import { clubImage } from '../utils/clubImages'
import { clubCategoryOptions } from '../utils/clubCategories'
const authStore = useAuthStore()
const { currentUser, userLoading, hasCheckedSession } = storeToRefs(authStore)

const clubs = ref<Club[]>([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const categoryFilter = ref('all')
const hasLoadedOnce = ref(false)
const lastFetchedAt = ref<Date | null>(null)
const totalClubCount = ref(0)

const isOwner = computed(() => Boolean(currentUser.value?.isOwner))
const sessionReady = computed(() => hasCheckedSession.value || !userLoading.value)

// Create club modal state
const showCreateModal = ref(false)
const createLoading = ref(false)
const createError = ref('')
const newClub = ref<Partial<Club>>({
  name: '',
  aliasName: '',
  description: '',
  category: clubCategoryOptions[0]?.title ?? '',
  meetingSchedule: '',
  location: '',
  contactEmail: '',
  advisor: '',
})

const openCreateModal = () => {
  createError.value = ''
  newClub.value = {
    name: '',
    aliasName: '',
    description: '',
    category: clubCategoryOptions[0]?.title ?? '',
    meetingSchedule: '',
    location: '',
    contactEmail: '',
    advisor: '',
  }
  showCreateModal.value = true
}

const handleCreateClub = async () => {
  if (!newClub.value.name?.trim()) {
    createError.value = 'Club name is required.'
    return
  }
  if (!newClub.value.category?.trim()) {
    createError.value = 'Category is required.'
    return
  }
  createLoading.value = true
  createError.value = ''
  try {
    await createClub(newClub.value)
    showCreateModal.value = false
    await loadClubs()
  } catch (err) {
    createError.value = err instanceof Error ? err.message : 'Failed to create club'
  } finally {
    createLoading.value = false
  }
}

const loadClubs = async () => {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    // fetchAllClubs pages past the backend's single-page cap (100, see
    // ClubService.findAllPaginated) on its own, so this always gets the
    // complete roster instead of only the first page.
    const [clubList, clubCount] = await Promise.all([
      fetchAllClubs(true),
      fetchClubCount(),
    ])
    clubs.value = clubList
    totalClubCount.value = clubCount
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
      totalClubCount.value = 0
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
            <strong>{{ totalClubCount }}</strong>
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
        <button type="button" class="primary-btn" @click="openCreateModal">+ Create Club</button>
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
      <div v-if="!filteredClubs.length && !clubs.length" class="status-card muted">
        <p>No clubs yet. Create the first one to get started.</p>
        <button type="button" class="primary-btn" @click="openCreateModal">+ Create Club</button>
      </div>
      <div v-else-if="filteredClubs.length" class="club-table">
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

    <!-- Create Club Modal -->
    <Teleport to="body">
      <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
        <div class="modal-card">
          <div class="modal-header">
            <h2>Create Club</h2>
            <button type="button" class="ghost-btn small" @click="showCreateModal = false" aria-label="Close">&times;</button>
          </div>
          <form class="modal-body" @submit.prevent="handleCreateClub">
            <label>
              <span>Club name <em>(required)</em></span>
              <input v-model="newClub.name" type="text" placeholder="e.g. Robotics Club" required />
            </label>
            <label>
              <span>Alias / short name</span>
              <input v-model="newClub.aliasName" type="text" placeholder="e.g. RC" />
            </label>
            <label>
              <span>Category <em>(required)</em></span>
              <select v-model="newClub.category" required>
                <option v-for="cat in clubCategoryOptions" :key="cat.title" :value="cat.title">{{ cat.title }}</option>
              </select>
            </label>
            <label>
              <span>Description</span>
              <textarea v-model="newClub.description" rows="3" placeholder="What does this club do?" />
            </label>
            <label>
              <span>Meeting schedule</span>
              <input v-model="newClub.meetingSchedule" type="text" placeholder="e.g. Tuesday · Weekly · Lunch" />
            </label>
            <label>
              <span>Location</span>
              <input v-model="newClub.location" type="text" placeholder="e.g. Room 411" />
            </label>
            <label>
              <span>Contact email</span>
              <input v-model="newClub.contactEmail" type="email" placeholder="e.g. president@school.org" />
            </label>
            <label>
              <span>Advisor name</span>
              <input v-model="newClub.advisor" type="text" placeholder="e.g. Dr. Smith" />
            </label>
            <div v-if="createError" class="status-card error">{{ createError }}</div>
            <div class="modal-actions">
              <button type="button" class="ghost-btn" @click="showCreateModal = false">Cancel</button>
              <button type="submit" class="primary-btn" :disabled="createLoading">
                {{ createLoading ? 'Creating…' : 'Create Club' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
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
  border: 1px solid var(--mv-border);
  padding: clamp(1.5rem, 4vw, 3rem);
  background: var(--mv-surface-hero-strong);
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
  border: 1px solid var(--mv-border);
  padding: 0.75rem 1.1rem;
  background: var(--mv-surface-card);
  min-width: 140px;
}

.stat-card span {
  font-size: 0.85rem;
  color: var(--mv-text-faint);
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
  color: var(--mv-text-dim);
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
  color: var(--mv-text-soft);
}

input,
select {
  border-radius: 16px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  color: var(--mv-text);
  padding: 0.65rem 0.9rem;
  min-width: 220px;
}

.club-table {
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card-strong);
  overflow: hidden;
}

.club-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.75rem;
  border-bottom: 1px solid var(--mv-border);
  flex-wrap: wrap;
  content-visibility: auto;
  contain-intrinsic-size: 110px;
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
  border: 1px solid var(--mv-border);
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
  color: var(--mv-text-faint);
}

.alias.muted {
  color: var(--mv-text-dim);
}

.club-meta {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: center;
  color: var(--mv-text-faint);
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
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
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
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  border-color: var(--mv-primary-bg);
}

.ghost-btn.small,
.primary-btn.small {
  padding: 0.4rem 1rem;
}

.status-card {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  padding: 1rem 1.25rem;
  background: var(--mv-surface-card-strong);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: var(--mv-status-danger);
}

.status-card.muted {
  color: var(--mv-text-faint);
}

.gate-shell {
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.gate-card {
  border-radius: 32px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.75rem, 4vw, 3rem);
  background: var(--mv-surface-card-strong);
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

  .club-main,
  .club-meta {
    min-width: 0;
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

/* Row layout only: flex-basis sets width here. In the max-width:700 column
   layout above, flex-basis would set height instead, so this range stops
   short of that breakpoint. */
@media (min-width: 701px) and (max-width: 900px) {
  .club-main {
    min-width: 0;
    flex: 1 1 220px;
  }

  .club-meta {
    min-width: 0;
    flex: 1 1 200px;
  }
}

@media (max-width: 720px) {
  .owner-hero {
    flex-direction: column;
    border-radius: 24px;
    padding: 1.5rem;
  }

  .hero-copy {
    flex: 1 1 100%;
  }

  .hero-actions {
    align-items: flex-start;
  }

  .stat-card {
    min-width: 120px;
    padding: 0.65rem 0.9rem;
  }

  .stat-card strong {
    font-size: 1.2rem;
  }

  .club-table {
    border-radius: 20px;
  }

  .club-row {
    padding: 1rem 1.1rem;
  }
}

@media (max-width: 480px) {
  .owner-hero {
    padding: 1.15rem;
    border-radius: 20px;
  }

  .hero-copy h1 {
    font-size: 1.5rem;
  }

  .hero-stats {
    gap: 0.5rem;
  }

  .stat-card {
    flex: 1 1 calc(50% - 0.5rem);
    min-width: 0;
  }

  .row-actions {
    flex-direction: column;
    gap: 0.5rem;
    width: 100%;
  }

  .row-actions .ghost-btn,
  .row-actions .primary-btn {
    width: 100%;
    text-align: center;
  }
}
/* ---- Create Club Modal ---- */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.modal-card {
  background: var(--mv-surface-card-strong);
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  max-width: 560px;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--mv-border);
}

.modal-header h2 {
  margin: 0;
  font-size: 1.25rem;
}

.modal-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem 1.5rem 1.5rem;
}

.modal-body label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.modal-body label span em {
  font-style: normal;
  color: var(--mv-status-danger);
}

.modal-body input,
.modal-body select,
.modal-body textarea {
  padding: 0.6rem 0.85rem;
  border-radius: 12px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  font-size: 0.95rem;
  font-family: inherit;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

</style>
