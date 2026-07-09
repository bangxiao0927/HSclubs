<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { storeToRefs } from 'pinia'
import type { Club } from '../types/club'
import { clubImage } from '../utils/clubImages'
import { buildApiUrl } from '../services/httpClient'

const authStore = useAuthStore()
const { isAuthenticated } = storeToRefs(authStore)

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')

const loadRecommendations = async () => {
  loading.value = true
  error.value = ''
  try {
    const url = buildApiUrl('/api/clubs/recommendations?limit=12')
    const response = await fetch(url, { credentials: 'include' })
    if (!response.ok) {
      const msg = await response.text()
      throw new Error(msg || 'Failed to load recommendations')
    }
    clubs.value = await response.json()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load recommendations'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadRecommendations()
})
</script>

<template>
  <div class="recommendations-page page-shell">
    <header class="rec-hero">
      <h1>Recommended for You</h1>
      <p v-if="isAuthenticated">
        Clubs picked based on your interests and memberships.
      </p>
      <p v-else>
        Popular clubs across all categories. <RouterLink to="/auth">Sign in</RouterLink> for personalized picks.
      </p>
    </header>

    <div v-if="loading" class="status-card">Finding clubs for you…</div>
    <div v-else-if="error" class="status-card error">
      <p>{{ error }}</p>
      <button type="button" class="ghost-btn" @click="loadRecommendations">Try again</button>
    </div>
    <div v-else-if="clubs.length" class="rec-grid">
      <RouterLink
        v-for="club in clubs"
        :key="club.id"
        :to="`/clubs/${club.id}`"
        class="rec-card"
      >
        <div class="rec-avatar">
          <img :src="clubImage(club)" :alt="club.name" loading="lazy" />
        </div>
        <div class="rec-info">
          <h3>{{ club.name }}</h3>
          <span class="rec-category">{{ club.category }}</span>
          <span class="rec-meta">{{ club.memberCount }} members · {{ club.meetingSchedule }}</span>
        </div>
      </RouterLink>
    </div>
    <div v-else class="status-card muted">
      <p>No recommendations available yet. Join some clubs to get personalized suggestions!</p>
      <RouterLink to="/" class="primary-btn">Browse all clubs</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.recommendations-page {
  padding-block: clamp(2rem, 5vw, 3.5rem);
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.rec-hero h1 {
  font-size: 1.75rem;
  margin: 0 0 0.5rem;
}

.rec-hero p {
  color: var(--mv-text-faint);
  margin: 0;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.rec-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, transform 0.2s;
}

.rec-card:hover {
  border-color: var(--mv-gold);
  transform: translateY(-2px);
}

.rec-avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
}

.rec-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.rec-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.rec-info h3 {
  margin: 0;
  font-size: 1rem;
}

.rec-category {
  font-size: 0.8rem;
  color: var(--mv-gold);
}

.rec-meta {
  font-size: 0.8rem;
  color: var(--mv-text-faint);
}
</style>
