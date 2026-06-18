<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { clubImage } from '../utils/clubImages'

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')

const route = useRoute()
const schoolSlug = computed(() => {
  const slug = route.params.schoolSlug
  return typeof slug === 'string' ? slug : undefined
})
const currentHeroImageIndex = ref(0)
let heroInterval: number | undefined

const page = ref(0)
const pageSize = 50
const hasMore = ref(true)
const loadingMore = ref(false)

const loadClubs = async () => {
  loading.value = true
  error.value = ''
  try {
    const newClubs = await fetchClubs({ schoolSlug: schoolSlug.value, page: 0, size: pageSize })
    clubs.value = newClubs
    hasMore.value = newClubs.length >= pageSize
  } catch {
    error.value = 'Failed to load clubs'
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value++
  try {
    const moreClubs = await fetchClubs({ schoolSlug: schoolSlug.value, page: page.value, size: pageSize })
    clubs.value = [...clubs.value, ...moreClubs]
    hasMore.value = moreClubs.length >= pageSize
  } catch {
    // silently ignore load-more failures
  } finally {
    loadingMore.value = false
  }
}

onMounted(loadClubs)

onMounted(() => {
  startHeroInterval()
})

onUnmounted(() => {
  stopHeroInterval()
})

const topClubs = computed(() =>
  [...clubs.value].sort((a, b) => (b.memberCount ?? 0) - (a.memberCount ?? 0)).slice(0, 4),
)

const heroImages = [
  '/hsclubs1.jpg',
  '/hsclubs2.png',
  '/hsclubs3.png',
]

const stopHeroInterval = () => {
  if (heroInterval !== undefined) {
    window.clearInterval(heroInterval)
    heroInterval = undefined
  }
}

const startHeroInterval = () => {
  stopHeroInterval()
  heroInterval = window.setInterval(() => {
    currentHeroImageIndex.value = (currentHeroImageIndex.value + 1) % heroImages.length
  }, 3000)
}

const showHeroImage = (index: number) => {
  currentHeroImageIndex.value = index
  startHeroInterval()
}

</script>

<template>
  <div class="home">
    <section class="home-hero page-shell">
      <div class="hero-copy">
        <p class="section-label">Mountain View · Clubs</p>
        <h1>Club directory</h1>
        <p>
          Browse all active clubs, jump into details, and use the search bar above when you already know the name or topic you want.
        </p>
        <div class="stat-card">
          <span class="stat-label">Active clubs</span>
          <p class="stat-value">{{ clubs.length }}</p>
        </div>
      </div>
      <div class="hero-visual">
        <div class="hero-image-frame">
          <transition name="hero-slide" mode="out-in">
            <img
              :key="heroImages[currentHeroImageIndex]"
              class="hero-image hero-image-primary"
              :src="heroImages[currentHeroImageIndex]"
              alt=""
              loading="eager"
            />
          </transition>
          <div class="hero-dots" aria-label="Homepage images">
            <button
              v-for="(image, index) in heroImages"
              :key="image"
              class="hero-dot"
              :class="{ active: index === currentHeroImageIndex }"
              type="button"
              :aria-label="`Show image ${index + 1}`"
              :aria-pressed="index === currentHeroImageIndex"
              @click="showHeroImage(index)"
            />
          </div>
        </div>
      </div>
    </section>

    <section v-if="loading" class="status-banner">Loading clubs…</section>
    <section v-else-if="error" class="status-banner error">{{ error }}</section>

    <section class="top-clubs-section page-shell">
      <div class="section-heading">
        <p class="section-label">Top enrollment</p>
        <h2>Highest membership clubs</h2>
        <p class="section-subtitle">Sorted automatically from roster submissions.</p>
      </div>
      <div v-if="topClubs.length" class="top-grid">
        <RouterLink v-for="club in topClubs" :key="club.id" :to="schoolSlug ? `/schools/${schoolSlug}/clubs/${club.id}` : `/clubs/${club.id}`" custom v-slot="{ navigate }">
          <article
            class="top-card"
            role="link"
            tabindex="0"
            @click="(event) => navigate(event)"
            @keydown.enter.prevent="() => navigate()"
            @keydown.space.prevent="() => navigate()"
          >
            <div class="club-avatar large">
              <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
            </div>
            <span class="member-count">{{ club.memberCount }} members</span>
            <h3>{{ club.name }}</h3>
            <div class="card-meta">
              <span>{{ club.advisor }}</span>
              <span>{{ club.meetingSchedule }}</span>
            </div>
          </article>
        </RouterLink>
      </div>
      <div v-else-if="!loading && !error" class="empty-state">
        <p>No club membership data is available yet.</p>
      </div>
    </section>

    <section class="all-clubs-section page-shell">
      <div class="section-heading">
        <p class="section-label">Directory</p>
        <h2>All clubs</h2>
        <p class="section-subtitle">Use the header search to open a separate results page for club names, advisors, or keywords.</p>
      </div>
      <div v-if="clubs.length" class="club-directory">
        <RouterLink v-for="club in clubs" :key="club.id" :to="schoolSlug ? `/schools/${schoolSlug}/clubs/${club.id}` : `/clubs/${club.id}`" custom v-slot="{ navigate }">
          <div
            class="club-row"
            role="link"
            tabindex="0"
            @click="(event) => navigate(event)"
            @keydown.enter.prevent="() => navigate()"
            @keydown.space.prevent="() => navigate()"
          >
            <div class="club-main">
              <div class="club-avatar">
                <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
              </div>
              <div class="club-info">
                <h3>{{ club.name }}</h3>
              </div>
            </div>
            <div class="club-details">
              <span class="badge">{{ club.memberCount }} members</span>
              <span>{{ club.advisor }}</span>
              <span>{{ club.meetingSchedule }}</span>
            </div>
          </div>
        </RouterLink>
      </div>
      <div v-else-if="!loading && !error" class="empty-state">
        <p>No clubs are available yet.</p>
      </div>
      <div v-if="hasMore && clubs.length >= pageSize" class="load-more">
        <button type="button" class="btn ghost" @click="loadMore" :disabled="loadingMore">
          {{ loadingMore ? 'Loading…' : 'Load more' }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 5vw, 3.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.home-hero {
  border-radius: 36px;
  border: 1px solid var(--mv-border);
  padding: clamp(1.75rem, 4vw, 3rem);
  background: var(--mv-surface-hero);
  display: flex;
  justify-content: space-between;
  gap: 2rem;
  flex-wrap: wrap;
  box-shadow: var(--mv-shadow-card);
}

.hero-copy {
  max-width: 520px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hero-copy h1 {
  font-size: clamp(2rem, 4vw, 3rem);
  margin: 0;
}

.hero-copy p {
  color: var(--mv-text-muted);
}

.stat-card {
  width: fit-content;
  min-width: 180px;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  padding: 1.25rem;
}

.hero-visual {
  flex: 1 1 320px;
  min-width: min(320px, 100%);
  display: flex;
}

.hero-image-frame {
  width: 100%;
  height: clamp(260px, 32vw, 420px);
  border-radius: 28px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-hero-strong);
  display: block;
  padding: 1rem;
  overflow: hidden;
  position: relative;
}

.hero-image-frame :deep(.hero-slide-enter-active),
.hero-image-frame :deep(.hero-slide-leave-active) {
  position: absolute;
  inset: 1rem;
  transition: opacity 0.45s ease, transform 0.45s ease;
}

.hero-image {
  position: absolute;
  inset: 1rem;
  width: calc(100% - 2rem);
  height: calc(100% - 2rem);
  object-fit: cover;
  display: block;
  border-radius: 22px;
}

.hero-image-primary {
  min-width: 0;
}

.hero-dots {
  position: absolute;
  left: 50%;
  bottom: 1.85rem;
  transform: translateX(-50%);
  z-index: 2;
  display: flex;
  gap: 0.55rem;
  padding: 0.45rem 0.7rem;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(8px);
}

.hero-dot {
  width: 0.72rem;
  height: 0.72rem;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.hero-dot.active {
  background: var(--mv-gold);
  transform: scale(1.15);
}

.hero-dot:focus-visible {
  outline: 2px solid white;
  outline-offset: 2px;
}

.hero-slide-enter-from {
  opacity: 0;
  transform: translateX(18px);
}

.hero-slide-leave-to {
  opacity: 0;
  transform: translateX(-18px);
}

.stat-label {
  font-size: 0.9rem;
  color: var(--mv-text-faint);
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  margin: 0.2rem 0 0;
  color: var(--mv-gold);
}

.top-clubs-section,
.all-clubs-section {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}

.section-heading h2 {
  margin: 0;
}

.section-subtitle {
  margin: 0;
}

.top-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.25rem;
}

.empty-state {
  border-radius: 24px;
  border: 1px dashed var(--mv-border-strong);
  padding: 1.25rem 1.5rem;
  background: var(--mv-surface-muted);
  color: var(--mv-text-muted);
}

.top-card {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  padding: 1.5rem;
  background: var(--mv-surface-card);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  box-shadow: var(--mv-shadow-card);
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
  content-visibility: auto;
  contain-intrinsic-size: 260px;
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


.member-count {
  color: var(--mv-gold);
  font-weight: 600;
  font-size: 0.95rem;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: var(--mv-text-faint);
  font-size: 0.9rem;
}

.club-directory {
  border-radius: 28px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card-strong);
  box-shadow: var(--mv-shadow-elevated);
}

.club-row {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1.25rem 1.75rem;
  border-bottom: 1px solid var(--mv-border);
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  position: relative;
  content-visibility: auto;
  contain-intrinsic-size: 110px;
}

.club-main {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.club-row:last-of-type {
  border-bottom: none;
}

.club-info h3 {
  margin: 0;
}

.club-row:hover h3,
.club-row:focus-visible h3 {
  color: var(--mv-gold);
}

.club-row:hover,
.club-row:focus-visible {
  background: var(--mv-gold-soft);
}

.top-card:focus-visible,
.club-row:focus-visible {
  outline: 2px solid var(--mv-gold);
  outline-offset: 4px;
}

.club-details {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  color: var(--mv-text-faint);
}


.badge {
  padding: 0.2rem 0.75rem;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.15);
  color: var(--mv-gold);
  font-weight: 600;
}

.status-banner {
  width: var(--page-content-width);
  margin: 0 auto;
  padding: 0.75rem 1.5rem;
  border-radius: 16px;
  background: var(--mv-surface-accent);
  border: 1px solid var(--mv-border-strong);
  color: var(--mv-text-soft);
}

.status-banner.error {
  background: var(--mv-surface-danger);
  border-color: rgba(239, 68, 68, 0.35);
  color: var(--mv-status-danger);
}

@media (max-width: 640px) {
  .club-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-visual {
    width: 100%;
  }

  .hero-image-frame {
    height: 220px;
  }

  .hero-dots {
    bottom: 1.45rem;
  }
}
.load-more {
  display: flex;
  justify-content: center;
  padding-top: 1rem;
}
.load-more .btn {
  padding: 0.65rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
}
.load-more .btn:hover { background: var(--mv-surface-accent); }
.load-more .btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
