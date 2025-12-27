<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { clubs, getClubById } from '../data/clubs'

const route = useRoute()
const club = computed(() => getClubById(route.params.id as string))
const relatedClubs = computed(() => clubs.filter((item) => item.id !== club.value?.id).slice(0, 3))
</script>

<template>
  <section class="club-detail page-shell" v-if="club">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>

    <header class="club-hero">
      <div>
        <p class="section-label">{{ club.category }}</p>
        <h1>{{ club.name }}</h1>
        <p class="hero-meta">
          {{ club.meeting }} · Advisor {{ club.advisor }} · {{ club.members }} members
        </p>
        <p class="hero-description">{{ club.description }}</p>
      </div>
      <div class="hero-stats">
        <div class="stat-card">
          <span class="stat-label">Members</span>
          <p class="stat-value">{{ club.members }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Primary advisor</span>
          <p class="stat-value">{{ club.advisor }}</p>
        </div>
        <div class="stat-card">
          <span class="stat-label">Contact</span>
          <p class="stat-value">{{ club.contact }}</p>
        </div>
      </div>
    </header>

    <section class="club-body">
      <div class="spotlight">
        <h2>What we run</h2>
        <p>
          {{ club.description }} Use this guide to align with Mountain View's activities office, track recruiting, and
          prep for showcases.
        </p>
        <h3>Recent achievements</h3>
        <ul>
          <li v-for="achievement in club.achievements" :key="achievement">
            {{ achievement }}
          </li>
        </ul>
        <button type="button">Email {{ club.contact }} →</button>
      </div>

      <aside class="related" v-if="relatedClubs.length">
        <h3>Also trending</h3>
        <ul>
          <li v-for="item in relatedClubs" :key="item.id">
            <RouterLink :to="`/clubs/${item.id}`">
              <span>{{ item.name }}</span>
              <small>{{ item.members }} members</small>
            </RouterLink>
          </li>
        </ul>
      </aside>
    </section>
  </section>

  <section v-else class="club-detail page-shell empty-state">
    <RouterLink to="/" class="back-link">← Back to clubs</RouterLink>
    <h1>Club not found</h1>
    <p>The club you requested is unavailable. Pick another entry from the directory.</p>
  </section>
</template>

<style scoped>
.club-detail {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.back-link {
  color: rgba(254, 252, 232, 0.7);
  font-weight: 600;
}

.club-hero {
  border-radius: 32px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  padding: clamp(1.5rem, 4vw, 3rem);
  background: linear-gradient(135deg, rgba(250, 204, 21, 0.18), rgba(5, 5, 5, 0.9));
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.club-hero h1 {
  margin: 0.25rem 0 0.5rem;
  font-size: clamp(2rem, 4vw, 3.2rem);
}

.hero-meta {
  margin: 0;
  color: rgba(254, 252, 232, 0.75);
}

.hero-description {
  color: var(--mv-text-muted);
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-card {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  padding: 1.2rem;
  background: rgba(0, 0, 0, 0.45);
}

.stat-label {
  font-size: 0.85rem;
  color: rgba(254, 252, 232, 0.7);
}

.stat-value {
  margin: 0.4rem 0 0;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--mv-gold);
}

.club-body {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: clamp(1rem, 4vw, 2rem);
  align-items: flex-start;
}

.spotlight {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.15);
  padding: clamp(1.5rem, 4vw, 2.75rem);
  background: rgba(7, 7, 7, 0.9);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.spotlight ul {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--mv-text);
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.spotlight button {
  align-self: flex-start;
  border-radius: 20px;
  border: 1px solid rgba(250, 204, 21, 0.35);
  background: rgba(250, 204, 21, 0.15);
  color: var(--mv-gold);
  padding: 0.65rem 1.6rem;
  font-weight: 600;
  cursor: pointer;
}

.related {
  border-radius: 24px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  padding: 1.5rem;
  background: rgba(10, 10, 10, 0.85);
}

.related ul {
  list-style: none;
  margin: 1rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.related a {
  display: flex;
  flex-direction: column;
  color: inherit;
}

.related small {
  color: rgba(254, 252, 232, 0.6);
}

.empty-state {
  gap: 0.75rem;
}

@media (max-width: 900px) {
  .club-body {
    grid-template-columns: 1fr;
  }
}
</style>
