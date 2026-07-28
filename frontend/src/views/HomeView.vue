<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import { fetchAllClubs, fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { clubImage } from '../utils/clubImages'
import { schoolTemplate } from '../config/schoolTemplate'

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')

const route = useRoute()
const schoolDisplayName = computed(() => schoolTemplate.schoolName)
const schoolShortName = computed(() => schoolTemplate.shortName)
const currentHeroImageIndex = ref(0)
const heroClubs = ref<Club[]>([])
const totalClubCount = ref(0)
let heroInterval: number | undefined

const page = ref(0)
const pageSize = 50
const hasMore = ref(true)
const loadingMore = ref(false)

const pickRandomInstagramClubs = (source: Club[], limit = 4) => {
  const candidates = source.filter((club) => Boolean(club.instagramUrl?.trim()))
  for (let index = candidates.length - 1; index > 0; index--) {
    const randomIndex = Math.floor(Math.random() * (index + 1))
    ;[candidates[index], candidates[randomIndex]] = [candidates[randomIndex]!, candidates[index]!]
  }
  return candidates.slice(0, limit)
}

const setHeroClubs = (source: Club[]) => {
  heroClubs.value = pickRandomInstagramClubs(source)
  currentHeroImageIndex.value = 0
  startHeroInterval()
}

const loadClubs = async () => {
  loading.value = true
  error.value = ''
  try {
    const allClubs = await fetchAllClubs()
    clubs.value = allClubs.slice(0, pageSize)
    totalClubCount.value = allClubs.length
    hasMore.value = allClubs.length > pageSize
    setHeroClubs(allClubs)
  } catch {
    clubs.value = []
    totalClubCount.value = 0
    hasMore.value = false
    error.value = 'The club directory is temporarily unavailable. Please try again later.'
    setHeroClubs([])
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value++
  try {
    const moreClubs = await fetchClubs({ page: page.value, size: pageSize })
    clubs.value = [...clubs.value, ...moreClubs]
    hasMore.value = moreClubs.length >= pageSize
  } catch {
    // silently ignore load-more failures
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  void loadClubs()
})

onUnmounted(() => {
  stopHeroInterval()
})

const topClubs = computed(() =>
  [...clubs.value].sort((a, b) => (b.memberCount ?? 0) - (a.memberCount ?? 0)).slice(0, 4),
)

const activeHeroClub = computed(() => heroClubs.value[currentHeroImageIndex.value] ?? null)

const stopHeroInterval = () => {
  if (heroInterval !== undefined) {
    window.clearInterval(heroInterval)
    heroInterval = undefined
  }
}

const startHeroInterval = () => {
  stopHeroInterval()
  if (heroClubs.value.length <= 1) {
    return
  }
  heroInterval = window.setInterval(() => {
    currentHeroImageIndex.value = (currentHeroImageIndex.value + 1) % heroClubs.value.length
  }, 6000)
}

const changeHeroImage = (offset: number) => {
  const count = heroClubs.value.length
  if (count <= 1) {
    return
  }
  currentHeroImageIndex.value = (currentHeroImageIndex.value + offset + count) % count
  startHeroInterval()
}
</script>

<template>
  <div class="home">
    <section class="home-hero page-shell">
      <div class="hero-copy">
        <p class="section-label">{{ schoolShortName }} · Clubs</p>
        <h1>{{ schoolDisplayName }} club directory template</h1>
        <p>{{ schoolTemplate.intro }}</p>
        <div class="hero-stats-inline">
          <div class="stat-card">
            <span class="stat-label">Active clubs</span>
            <p class="stat-value">{{ totalClubCount }}</p>
          </div>
        </div>
      </div>
      <div class="hero-visual">
        <div class="hero-image-frame">
          <transition name="hero-slide" mode="out-in">
            <RouterLink
              v-if="activeHeroClub"
              :key="activeHeroClub.id"
              class="hero-image-link"
              :to="`/clubs/${activeHeroClub.id}`"
              :aria-label="`View ${activeHeroClub.name}`"
            >
              <img
                class="hero-image hero-image-primary"
                :src="clubImage(activeHeroClub)"
                :alt="`${activeHeroClub.name} avatar`"
                loading="eager"
              />
              <span class="hero-club-label">{{ activeHeroClub.name }}</span>
            </RouterLink>
            <div v-else key="no-instagram-clubs" class="hero-image-empty">
              Instagram club photos will appear here when available.
            </div>
          </transition>
          <div v-if="heroClubs.length > 1" class="hero-navigation" aria-label="Featured clubs">
            <button
              class="hero-arrow hero-arrow-previous"
              type="button"
              aria-label="Show previous club"
              @click="changeHeroImage(-1)"
            >
              &lsaquo;
            </button>
            <button
              class="hero-arrow hero-arrow-next"
              type="button"
              aria-label="Show next club"
              @click="changeHeroImage(1)"
            >
              &rsaquo;
            </button>
          </div>
        </div>
      </div>
    </section>

    <section v-if="loading" class="page-shell"><SkeletonLoader :count="4" /></section>
    <section v-else-if="error" class="status-banner error">{{ error }}</section>

    <section class="top-clubs-section page-shell">
      <div class="section-heading">
        <p class="section-label">Top enrollment</p>
        <h2>Highest membership clubs</h2>
        <p class="section-subtitle">
          Use this area to highlight the most active clubs at your school.
        </p>
      </div>
      <div v-if="topClubs.length" class="top-grid">
        <RouterLink
          v-for="club in topClubs"
          :key="club.id"
          :to="`/clubs/${club.id}`"
          custom
          v-slot="{ navigate }"
        >
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
        <p class="section-subtitle">
          Browse clubs by name, advisor, meeting time, or keyword.
        </p>
      </div>
      <div v-if="clubs.length" class="club-directory">
        <RouterLink
          v-for="club in clubs"
          :key="club.id"
          :to="`/clubs/${club.id}`"
          custom
          v-slot="{ navigate }"
        >
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

.hero-stats-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
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
  transition:
    opacity 0.45s ease,
    transform 0.45s ease;
}

.hero-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 22px;
}

.hero-image-link {
  position: absolute;
  inset: 1rem;
  display: block;
  color: white;
}

.hero-image-link:focus-visible {
  outline: 3px solid var(--mv-gold);
  outline-offset: 3px;
  border-radius: 22px;
}

.hero-club-label {
  position: absolute;
  right: 1rem;
  bottom: 1rem;
  left: 1rem;
  padding: 0.7rem 0.9rem;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(8px);
  font-weight: 700;
  text-align: center;
}

.hero-image-empty {
  position: absolute;
  inset: 1rem;
  display: grid;
  place-items: center;
  padding: 2rem;
  border-radius: 22px;
  color: var(--mv-text-muted);
  text-align: center;
}

.hero-image-primary {
  min-width: 0;
}

.hero-navigation {
  position: absolute;
  inset: 50% 1.6rem auto;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  justify-content: space-between;
  pointer-events: none;
}

.hero-arrow {
  width: 2.75rem;
  height: 2.75rem;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.58);
  color: white;
  cursor: pointer;
  pointer-events: auto;
  font-size: 2rem;
  line-height: 1;
  transition:
    transform 0.2s ease,
    background 0.2s ease;
}

.hero-arrow:hover {
  background: rgba(15, 23, 42, 0.82);
  transform: scale(1.06);
}

.hero-arrow:focus-visible {
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
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
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
  background: var(--mv-gold-soft);
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

  .hero-navigation {
    inset: 50% 1.25rem auto;
  }

  .hero-arrow {
    width: 2.4rem;
    height: 2.4rem;
  }
}

@media (max-width: 720px) {
  .home-hero {
    flex-direction: column;
    gap: 1.5rem;
  }

  .hero-copy {
    max-width: 100%;
  }

  .home-hero {
    padding: 1.5rem;
    border-radius: 24px;
  }

  .club-directory {
    border-radius: 20px;
  }

  .club-row {
    padding: 1rem 1.25rem;
  }

  .club-main {
    gap: 0.75rem;
  }

  .club-avatar {
    width: 56px;
    height: 56px;
    border-radius: 16px;
  }

  .top-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 0.85rem;
  }

  .top-card {
    padding: 1.1rem;
    border-radius: 18px;
  }
}

@media (max-width: 480px) {
  .home-hero {
    padding: 1.25rem;
    border-radius: 20px;
  }

  .hero-copy h1 {
    font-size: 1.6rem;
  }

  .top-grid {
    grid-template-columns: 1fr;
  }

  .club-details {
    gap: 0.5rem;
    font-size: 0.85rem;
  }

  .club-avatar.large {
    width: 72px;
    height: 72px;
    border-radius: 20px;
  }

  .stat-card {
    min-width: 140px;
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
.load-more .btn:hover {
  background: var(--mv-surface-accent);
}
.load-more .btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
