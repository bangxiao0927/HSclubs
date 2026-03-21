<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchClubs } from '../services/clubService'
import type { Club } from '../types/club'

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

const topClubs = computed(() =>
  [...clubs.value].sort((a, b) => (b.memberCount ?? 0) - (a.memberCount ?? 0)).slice(0, 4),
)

const clubImage = (club: Club) => club.imageUrl ?? `https://api.dicebear.com/7.x/thumbs/svg?seed=${encodeURIComponent(club.name)}`
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
      <div class="hero-visual" aria-hidden="true">
        <div class="hero-image-frame">
          <span class="frame-label">Cover image area</span>
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
        <RouterLink v-for="club in topClubs" :key="club.id" :to="`/clubs/${club.id}`" custom v-slot="{ navigate }">
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
        <RouterLink v-for="club in clubs" :key="club.id" :to="`/clubs/${club.id}`" custom v-slot="{ navigate }">
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
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: clamp(1.75rem, 4vw, 3rem);
  background: linear-gradient(125deg, rgba(250, 204, 21, 0.18), rgba(5, 5, 5, 0.9));
  display: flex;
  justify-content: space-between;
  gap: 2rem;
  flex-wrap: wrap;
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
  border: 1px solid rgba(250, 204, 21, 0.2);
  background: rgba(0, 0, 0, 0.45);
  padding: 1.25rem;
}

.hero-visual {
  flex: 1 1 320px;
  min-width: min(320px, 100%);
  display: flex;
}

.hero-image-frame {
  width: 100%;
  min-height: 240px;
  border-radius: 28px;
  border: 1px dashed rgba(250, 204, 21, 0.35);
  background:
    radial-gradient(circle at 20% 20%, rgba(250, 204, 21, 0.18), transparent 35%),
    linear-gradient(145deg, rgba(24, 24, 24, 0.95), rgba(8, 8, 8, 0.98));
  display: flex;
  align-items: flex-end;
  padding: 1.25rem;
}

.frame-label {
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  border: 1px solid rgba(250, 204, 21, 0.28);
  background: rgba(0, 0, 0, 0.35);
  color: rgba(254, 252, 232, 0.72);
  font-size: 0.82rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.stat-label {
  font-size: 0.9rem;
  color: rgba(254, 252, 232, 0.7);
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
  border: 1px dashed rgba(250, 204, 21, 0.28);
  padding: 1.25rem 1.5rem;
  background: rgba(10, 10, 10, 0.55);
  color: var(--mv-text-muted);
}

.top-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  padding: 1.5rem;
  background: rgba(10, 10, 10, 0.85);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  box-shadow: 0 25px 40px rgba(0, 0, 0, 0.35);
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


.member-count {
  color: var(--mv-gold);
  font-weight: 600;
  font-size: 0.95rem;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  color: rgba(254, 252, 232, 0.7);
  font-size: 0.9rem;
}

.club-directory {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.15);
  background: rgba(7, 7, 7, 0.9);
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.45);
}

.club-row {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1.25rem 1.75rem;
  border-bottom: 1px solid rgba(254, 252, 232, 0.05);
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
  background: rgba(253, 224, 71, 0.05);
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
  color: rgba(254, 252, 232, 0.75);
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
  background: rgba(253, 224, 71, 0.12);
  border: 1px solid rgba(253, 224, 71, 0.35);
  color: rgba(254, 252, 232, 0.85);
}

.status-banner.error {
  background: rgba(248, 113, 113, 0.12);
  border-color: rgba(248, 113, 113, 0.45);
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
    min-height: 180px;
  }
}
</style>
