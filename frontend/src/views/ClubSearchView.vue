<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { matchesClubSearch, normalizeClubSearchQuery } from '../utils/clubSearch'
import { clubImage } from '../utils/clubImages'

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')
const route = useRoute()

const loadClubs = async () => {
  loading.value = true
  error.value = ''
  try {
    clubs.value = await fetchClubs()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load clubs'
  } finally {
    loading.value = false
  }
}

onMounted(loadClubs)

const rawSearchQuery = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const normalizedSearchQuery = computed(() => normalizeClubSearchQuery(route.query.q))
const filteredClubs = computed(() =>
  clubs.value.filter((club) => matchesClubSearch(club, normalizedSearchQuery.value)),
)
</script>

<template>
  <div class="search-results">
    <section class="results-hero page-shell">
      <div class="results-copy">
        <p class="section-label">Search results</p>
        <h1 v-if="rawSearchQuery">Results for "{{ rawSearchQuery }}"</h1>
        <h1 v-else>Search clubs</h1>
        <p v-if="rawSearchQuery">
          {{ filteredClubs.length }} matching club<span v-if="filteredClubs.length !== 1">s</span> across names, advisors, locations, and descriptions.
        </p>
        <p v-else>Enter a club name or keyword in the header search bar to browse the directory from this page.</p>
      </div>
    </section>

    <section v-if="loading" class="status-banner">Loading clubs…</section>
    <section v-else-if="error" class="status-banner error">{{ error }}</section>

    <section class="results-section page-shell">
      <div v-if="rawSearchQuery && filteredClubs.length" class="results-list">
        <RouterLink v-for="club in filteredClubs" :key="club.id" :to="`/clubs/${club.id}`" custom v-slot="{ navigate }">
          <article
            class="result-card"
            role="link"
            tabindex="0"
            @click="(event) => navigate(event)"
            @keydown.enter.prevent="() => navigate()"
            @keydown.space.prevent="() => navigate()"
          >
            <div class="result-main">
              <div class="club-avatar">
                <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
              </div>
              <div class="result-copy">
                <h2>{{ club.name }}</h2>
                <p>{{ club.description }}</p>
              </div>
            </div>
            <div class="result-meta">
              <span class="badge">{{ club.memberCount }} members</span>
              <span>{{ club.advisor || 'Advisor TBD' }}</span>
              <span>{{ club.meetingSchedule || 'Schedule TBD' }}</span>
              <span>{{ club.location || 'Location TBD' }}</span>
            </div>
          </article>
        </RouterLink>
      </div>

      <div v-else-if="rawSearchQuery && !loading && !error" class="empty-state">
        <p>No clubs match "{{ rawSearchQuery }}". Try another club name, advisor, or keyword.</p>
      </div>

      <div v-else-if="!loading && !error" class="empty-state">
        <p>Search from the header to open club results here.</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.search-results {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 5vw, 3.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.results-hero {
  border-radius: 36px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: clamp(1.75rem, 4vw, 3rem);
  background: linear-gradient(135deg, rgba(250, 204, 21, 0.12), rgba(10, 10, 10, 0.95));
}

.results-copy {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  max-width: 720px;
}

.results-copy h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3rem);
}

.results-copy p {
  margin: 0;
  color: var(--mv-text-muted);
}

.results-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.results-list {
  display: grid;
  gap: 1rem;
}

.result-card {
  display: flex;
  justify-content: space-between;
  gap: 1.25rem;
  padding: 1.4rem 1.5rem;
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.16);
  background: rgba(7, 7, 7, 0.88);
  color: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, background 0.2s ease;
  content-visibility: auto;
  contain-intrinsic-size: 180px;
}

.result-card:hover,
.result-card:focus-visible {
  border-color: rgba(250, 204, 21, 0.45);
  background: rgba(20, 20, 20, 0.96);
  transform: translateY(-2px);
}

.result-card:focus-visible {
  outline: 2px solid var(--mv-gold);
  outline-offset: 4px;
}

.result-main {
  display: flex;
  gap: 1rem;
  min-width: 0;
}

.club-avatar {
  width: 80px;
  height: 80px;
  border-radius: 22px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.3);
  background: rgba(253, 224, 71, 0.12);
  flex-shrink: 0;
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.result-copy {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
}

.result-copy h2 {
  margin: 0;
}

.result-copy p {
  margin: 0;
  color: rgba(254, 252, 232, 0.72);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
  color: rgba(254, 252, 232, 0.74);
  text-align: right;
  flex-shrink: 0;
}

.badge {
  padding: 0.2rem 0.75rem;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.15);
  color: var(--mv-gold);
  font-weight: 600;
}

.empty-state {
  border-radius: 24px;
  border: 1px dashed rgba(250, 204, 21, 0.28);
  padding: 1.25rem 1.5rem;
  background: rgba(10, 10, 10, 0.55);
  color: var(--mv-text-muted);
}

.status-banner {
  width: var(--page-content-width);
  margin: 0 auto;
  padding: 0.75rem 1.5rem;
  border-radius: 16px;
  background: rgba(253, 224, 71, 0.12);
  border: 1px solid rgba(253, 224, 71, 0.35);
  color: rgba(254, 252, 232, 0.85);
}

.status-banner.error {
  background: rgba(248, 113, 113, 0.12);
  border-color: rgba(248, 113, 113, 0.45);
}

@media (max-width: 720px) {
  .result-card {
    flex-direction: column;
  }

  .result-meta {
    align-items: flex-start;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .result-card {
    padding: 1.15rem;
  }

  .result-main {
    align-items: flex-start;
  }

  .club-avatar {
    width: 64px;
    height: 64px;
    border-radius: 18px;
  }
}
</style>
