<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchAllClubs } from '../services/clubService'
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
    clubs.value = await fetchAllClubs()
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
  () =>
    categories.value.find((category) => category.title === selectedCategoryTitle.value) ??
    categories.value[0] ??
    null,
)
</script>

<template>
  <section class="explore page-shell">
    <header class="explore-header">
      <p class="section-label">Explore</p>
      <h1>Browse clubs by category</h1>
      <p>
        Clubs are grouped by their saved category, and club admins can now maintain that type
        directly from the admin page.
      </p>
    </header>

    <section v-if="loading" class="status-card">Loading categories…</section>
    <section v-else-if="error" class="status-card error">{{ error }}</section>

    <template v-else>
      <RouterLink to="/recommendations" class="quiz-link">
        <span>Get your interest club</span>
        <span aria-hidden="true">→</span>
      </RouterLink>

      <div class="category-picker">
        <button
          v-for="category in categories"
          :key="category.title"
          type="button"
          class="category-pill"
          :class="{ active: category.title === activeCategory?.title }"
          :aria-pressed="category.title === activeCategory?.title"
          @click="selectedCategoryTitle = category.title"
        >
          <span aria-hidden="true">{{ category.icon }}</span>
          <span class="category-pill-title">{{ category.title }}</span>
          <small>{{ category.clubCount }}</small>
        </button>
      </div>

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
          <RouterLink
            v-for="club in activeCategory.clubs"
            :key="club.id"
            class="top-card"
            :to="`/clubs/${club.id}`"
          >
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
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-card);
}

.status-card.error {
  border-color: rgba(239, 68, 68, 0.35);
  color: var(--mv-status-danger);
}

.quiz-link {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  width: 100%;
  min-height: 144px;
  padding: 2rem clamp(3.5rem, 6vw, 5rem);
  border: 1px solid var(--mv-primary-bg);
  border-radius: 22px;
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
  font-size: clamp(1rem, 2vw, 1.2rem);
  font-weight: 700;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.quiz-link:hover,
.quiz-link:focus-visible {
  transform: translateY(-2px);
}

.quiz-link:focus-visible {
  outline: 3px solid var(--mv-gold);
  outline-offset: 3px;
}

.quiz-link span:last-child {
  position: absolute;
  right: clamp(1.25rem, 3vw, 2rem);
  font-size: 1.5rem;
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
  min-height: 44px;
  padding: 0.7rem 1rem;
  border-radius: 999px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-muted);
  color: inherit;
  cursor: pointer;
  font-weight: 600;
}

.category-pill-title {
  min-width: 0;
  overflow-wrap: anywhere;
}

.category-pill small {
  flex-shrink: 0;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--mv-surface-soft);
  color: var(--mv-text-faint);
}

.category-pill.active {
  border-color: var(--mv-primary-bg);
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
}

.category-pill.active small {
  background: rgba(255, 255, 255, 0.22);
  color: inherit;
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
  border: 1px solid var(--mv-border);
  color: var(--mv-text-soft);
  font-size: 0.9rem;
}

.top-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.25rem;
}

.top-card {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  padding: 1.5rem;
  background: var(--mv-surface-card);
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  box-shadow: var(--mv-shadow-card);
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
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
  border-color: var(--mv-border-strong);
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
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-accent);
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
  color: var(--mv-gold);
  font-size: 0.9rem;
}

.club-description {
  margin: 0;
  color: var(--mv-text-faint);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: var(--mv-text-faint);
  font-size: 0.9rem;
}

.empty-card {
  border-radius: 24px;
  border: 1px dashed var(--mv-border-strong);
  padding: 1.5rem;
  background: var(--mv-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mv-text-muted);
}

@media (max-width: 720px) {
  .quiz-link {
    min-height: 96px;
    padding: 1.25rem 2.5rem 1.25rem 1.1rem;
    border-radius: 18px;
    text-align: left;
    justify-content: flex-start;
  }

  /* Two compact columns instead of one full-width pill per category: the six
     categories stay visible together instead of pushing the club list a full
     screen down. */
  .category-picker {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.5rem;
  }

  .category-pill {
    justify-content: flex-start;
    gap: 0.4rem;
    padding: 0.6rem 0.7rem;
    border-radius: 14px;
    font-size: 0.85rem;
    line-height: 1.25;
    text-align: left;
  }

  .category-pill small {
    margin-left: auto;
    font-size: 0.75rem;
  }

  .top-grid {
    grid-template-columns: 1fr;
    gap: 0.85rem;
  }

  .top-card {
    padding: 1.1rem;
    border-radius: 18px;
  }

  .clubs-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .club-avatar.large {
    width: 64px;
    height: 64px;
    border-radius: 18px;
  }

  .club-description {
    font-size: 0.9rem;
    -webkit-line-clamp: 3;
  }

  .card-meta {
    font-size: 0.82rem;
  }
}
</style>
