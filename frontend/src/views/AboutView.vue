<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { clubCategoryOptions } from '../utils/clubCategories'
import { clubImage } from '../utils/clubImages'

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')
const selectedCategoryTitle = ref(clubCategoryOptions[0]?.title ?? '')

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

const categories = computed(() =>
  clubCategoryOptions.map((category) => {
    const categoryClubs = clubs.value
      .filter((club) => club.category === category.title)
      .sort((a, b) => {
        const memberDelta = (b.memberCount ?? 0) - (a.memberCount ?? 0)
        return memberDelta !== 0 ? memberDelta : a.name.localeCompare(b.name)
      })

    return {
      ...category,
      clubCount: categoryClubs.length,
      clubs: categoryClubs,
    }
  }),
)

const activeCategory = computed(
  () => categories.value.find((category) => category.title === selectedCategoryTitle.value) ?? categories.value[0] ?? null,
)
</script>

<template>
  <section class="explore page-shell">
    <header class="explore-header">
      <p class="section-label">Explore</p>
      <h1>Browse clubs by category</h1>
      <p>
        Clubs are grouped by their saved category, and club admins can now maintain that type directly from the admin page.
      </p>
    </header>

    <section v-if="loading" class="status-card">Loading categories…</section>
    <section v-else-if="error" class="status-card error">{{ error }}</section>

    <template v-else>
      <section class="overview-grid">
        <article v-for="category in categories" :key="category.title" class="overview-card">
          <span class="overview-icon">{{ category.icon }}</span>
          <strong>{{ category.clubCount }}</strong>
          <span>{{ category.title }}</span>
        </article>
      </section>

      <div class="category-picker">
        <button
          v-for="category in categories"
          :key="category.title"
          type="button"
          class="category-pill"
          :class="{ active: category.title === activeCategory?.title }"
          @click="selectedCategoryTitle = category.title"
        >
          <span>{{ category.icon }}</span>
          <span>{{ category.title }}</span>
          <small>{{ category.clubCount }}</small>
        </button>
      </div>

      <article v-if="activeCategory" class="category-panel">
        <div class="panel-hero" :style="{ background: activeCategory.gradient }">
          <span class="panel-icon">{{ activeCategory.icon }}</span>
          <p class="panel-count">{{ activeCategory.clubCount }} clubs in this category</p>
          <h2>{{ activeCategory.title }}</h2>
          <p>{{ activeCategory.description }}</p>
          <span class="panel-focus">{{ activeCategory.focus }}</span>
        </div>
      </article>

      <section v-if="activeCategory" class="category-clubs">
        <header class="clubs-header">
          <div>
            <p class="section-label">Clubs in this category</p>
            <h2>{{ activeCategory.title }}</h2>
          </div>
          <p class="status-pill">{{ activeCategory.clubCount }} clubs</p>
        </header>

        <div class="top-grid">
          <article v-if="!activeCategory.clubs.length" class="empty-card">
            <p>No clubs have been assigned to {{ activeCategory.title }} yet.</p>
          </article>
          <RouterLink v-for="club in activeCategory.clubs" :key="club.id" class="top-card" :to="`/clubs/${club.id}`">
            <div class="club-avatar large">
              <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
            </div>
            <span class="member-count">{{ club.memberCount }} members</span>
            <h3>{{ club.name }}</h3>
            <p class="club-category">{{ club.category }}</p>
            <p class="club-description">{{ club.description }}</p>
            <div class="card-meta">
              <span>{{ club.advisor || 'Advisor TBD' }}</span>
              <span>{{ club.meetingSchedule || 'Schedule TBD' }}</span>
            </div>
          </RouterLink>
        </div>
      </section>
    </template>
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
  max-width: 700px;
  color: var(--mv-text-muted);
}

.status-card {
  padding: 0.9rem 1rem;
  border-radius: 20px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: rgba(10, 10, 10, 0.82);
}

.status-card.error {
  border-color: rgba(248, 113, 113, 0.45);
  color: #fecaca;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
}

.overview-card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 1.05rem 1.2rem;
  border-radius: 22px;
  border: 1px solid rgba(250, 204, 21, 0.16);
  background: rgba(10, 10, 10, 0.8);
}

.overview-card strong {
  font-size: 1.8rem;
  color: var(--mv-gold);
}

.overview-icon {
  font-size: 1.2rem;
}

.category-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.category-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.7rem 1rem;
  border-radius: 999px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-weight: 600;
}

.category-pill small {
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: rgba(254, 252, 232, 0.08);
  color: rgba(254, 252, 232, 0.72);
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

.panel-hero {
  padding: clamp(1.5rem, 4vw, 2.8rem);
  color: #fefce8;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
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

.panel-focus {
  width: fit-content;
  margin-top: 0.35rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.18);
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

.top-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
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
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
  content-visibility: auto;
  contain-intrinsic-size: 320px;
}

.top-card h3 {
  margin: 0;
}

.top-card:hover h3,
.top-card:focus-visible h3 {
  color: var(--mv-gold);
}

.top-card:hover,
.top-card:focus-visible {
  border-color: rgba(250, 204, 21, 0.45);
  transform: translateY(-2px);
}

.top-card:focus-visible {
  outline: 2px solid var(--mv-gold);
  outline-offset: 4px;
}

.club-avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(250, 204, 21, 0.3);
  background: rgba(253, 224, 71, 0.12);
  flex-shrink: 0;
}

.club-avatar.large {
  width: 96px;
  height: 96px;
  border-radius: 26px;
}

.club-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.member-count {
  color: var(--mv-gold);
  font-weight: 600;
  font-size: 0.95rem;
}

.club-category {
  margin: 0;
  color: rgba(250, 204, 21, 0.82);
  font-size: 0.9rem;
}

.club-description {
  margin: 0;
  color: rgba(254, 252, 232, 0.74);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  border: 1px dashed rgba(250, 204, 21, 0.22);
  padding: 1.5rem;
  background: rgba(10, 10, 10, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mv-text-muted);
}

@media (max-width: 640px) {
  .category-panel {
    border-radius: 24px;
  }

  .category-pill {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
