<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'

type Category = {
  id: string
  title: string
  focus: string
  clubCount: number
  icon: string
  gradient: string
}

const categories: Category[] = [
  {
    id: 'stem',
    title: 'STEM & Innovation',
    focus: 'Robotics · Engineering · AI',
    clubCount: 12,
    icon: '⚙️',
    gradient: 'linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%)'
  },
  {
    id: 'arts',
    title: 'Creative Arts & Media',
    focus: 'Design · Film · Performing Arts',
    clubCount: 15,
    icon: '🎨',
    gradient: 'linear-gradient(135deg, #854d0e 0%, #facc15 100%)'
  },
  {
    id: 'service',
    title: 'Service & Leadership',
    focus: 'Community · Civic Action · Advocacy',
    clubCount: 9,
    icon: '🤝',
    gradient: 'linear-gradient(135deg, #14532d 0%, #22c55e 100%)'
  },
  {
    id: 'wellness',
    title: 'Athletics & Wellness',
    focus: 'Training · Competition · Mindfulness',
    clubCount: 8,
    icon: '🏅',
    gradient: 'linear-gradient(135deg, #991b1b 0%, #dc2626 100%)'
  }
]

const fallbackCategory = categories[0]!
const selectedCategoryId = ref(fallbackCategory.id)
const activeCategory = computed(() => categories.find(category => category.id === selectedCategoryId.value) ?? fallbackCategory)

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')

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

const clubImage = (club: Club) => club.imageUrl ?? `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(club.name)}`
const activeCategoryClubs = computed(() =>
  clubs.value.filter((club) => club.category === activeCategory.value.title)
)
</script>

<template>
  <section class="explore page-shell">
    <header class="explore-header">
      <p class="section-label">Explore</p>
      <h1>Pick a focus area</h1>
      <p>
        Choose a category to see how Mountain View packages resources, advisors, and recruiting tips for that lane. Use it to guide your next signup push.
      </p>
    </header>

    <div class="category-picker">
      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        class="category-pill"
        :class="{ active: category.id === selectedCategoryId }"
        @click="selectedCategoryId = category.id"
      >
        <span>{{ category.icon }}</span>
        <span>{{ category.title }}</span>
      </button>
    </div>

    <article v-if="activeCategory" class="category-panel">
      <div
        class="panel-hero"
        :style="{ background: activeCategory.gradient }"
      >
        <span class="panel-icon">{{ activeCategory.icon }}</span>
        <p class="panel-count">{{ activeCategory.clubCount }} active clubs</p>
        <h2>{{ activeCategory.title }}</h2>
        <p>{{ activeCategory.focus }}</p>
      </div>
    </article>

    <section class="category-clubs" v-if="activeCategory">
      <header class="clubs-header">
        <div>
          <p class="section-label">Clubs in this category</p>
          <h2>{{ activeCategory.title }}</h2>
        </div>
        <p v-if="loading" class="status-pill">Loading…</p>
        <p v-else-if="error" class="status-pill error">{{ error }}</p>
        <p v-else class="status-pill">{{ activeCategoryClubs.length }} clubs</p>
      </header>

      <div v-if="!loading && !error" class="top-grid">
        <article v-if="!activeCategoryClubs.length" class="empty-card">
          <p>No clubs have been tagged for this category yet.</p>
        </article>
        <article v-for="club in activeCategoryClubs" :key="club.id" class="top-card">
          <div class="club-avatar large">
            <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
          </div>
          <div class="card-head">
            <p class="club-category">{{ club.category }}</p>
            <span class="member-count">{{ club.memberCount }} members</span>
          </div>
          <RouterLink :to="`/clubs/${club.id}`">
            <h3>{{ club.name }}</h3>
          </RouterLink>
          <p>{{ club.description }}</p>
          <div class="card-meta">
            <span>{{ club.advisor }}</span>
            <span>{{ club.meetingSchedule }}</span>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.explore {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.explore-header h1 {
  margin: 0.25rem 0 0.5rem;
}

.explore-header p {
  max-width: 640px;
  color: var(--mv-text-muted);
}

.category-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.category-pill {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 1.2rem;
  border-radius: 999px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-weight: 600;
}

.category-pill.active {
  background: rgba(250, 204, 21, 0.15);
  border-color: rgba(250, 204, 21, 0.45);
  color: var(--mv-gold);
}

.category-panel {
  border-radius: 32px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  overflow: hidden;
  background: rgba(5, 5, 5, 0.85);
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.45);
}

.category-clubs {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.clubs-header {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
}

.status-pill {
  padding: 0.35rem 0.9rem;
  border-radius: 999px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  color: rgba(254, 252, 232, 0.8);
  font-size: 0.9rem;
}

.status-pill.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: #fecaca;
}

.top-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.25rem;
}

.top-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: 1.5rem;
  background: rgba(10, 10, 10, 0.85);
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  box-shadow: 0 25px 40px rgba(0, 0, 0, 0.35);
}

.top-card h3 {
  margin: 0;
}

.top-card a {
  color: inherit;
}

.top-card a:hover h3 {
  color: var(--mv-gold);
}

.club-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: rgba(253, 224, 71, 0.08);
  flex-shrink: 0;
}

.club-avatar.large {
  width: 72px;
  height: 72px;
  border-radius: 20px;
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card-head {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
  color: rgba(254, 252, 232, 0.75);
}

.member-count {
  color: var(--mv-gold);
  font-weight: 600;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: rgba(254, 252, 232, 0.7);
  font-size: 0.9rem;
}

.empty-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: 1.5rem;
  background: rgba(10, 10, 10, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mv-text-muted);
}

.panel-hero {
  padding: clamp(1.5rem, 4vw, 2.8rem);
  color: #fefce8;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.panel-icon {
  font-size: 2rem;
}

.panel-count {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.85rem;
  color: rgba(254, 252, 232, 0.85);
}

@media (max-width: 640px) {
  .category-panel {
    border-radius: 24px;
  }
}
</style>
