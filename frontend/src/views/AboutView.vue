<script setup lang="ts">
import { computed, ref } from 'vue'

type Category = {
  id: string
  title: string
  focus: string
  summary: string
  clubCount: number
  icon: string
  gradient: string
  highlights: string[]
}

const categories: Category[] = [
  {
    id: 'stem',
    title: 'STEM & Innovation',
    focus: 'Robotics · Engineering · AI',
    summary: 'Prototype labs, coding collectives, and maker crews pushing Mountain View into regional competitions.',
    clubCount: 12,
    icon: '⚙️',
    gradient: 'linear-gradient(135deg, #0f172a 0%, #1d4ed8 100%)',
    highlights: ['Spartan Robotics build lab', 'Girls Who Code cohorts', 'BioResearch League partnerships']
  },
  {
    id: 'arts',
    title: 'Creative Arts & Media',
    focus: 'Design · Film · Performing Arts',
    summary: 'Studios that ship podcasts, stage productions, and gallery-ready visuals with the black + gold system baked in.',
    clubCount: 15,
    icon: '🎨',
    gradient: 'linear-gradient(135deg, #854d0e 0%, #facc15 100%)',
    highlights: ['Golden Sound Collective studio hours', 'Spartan Studio Theater repertory', 'Design sprints for pep media']
  },
  {
    id: 'service',
    title: 'Service & Leadership',
    focus: 'Community · Civic Action · Advocacy',
    summary: 'Coalitions partnering with the city, feeder schools, and regional nonprofits for measurable impact.',
    clubCount: 9,
    icon: '🤝',
    gradient: 'linear-gradient(135deg, #14532d 0%, #22c55e 100%)',
    highlights: ['Trail Stewards restoration crews', 'Model United Nations debate prep', 'District-wide mentorship pairs']
  },
  {
    id: 'wellness',
    title: 'Athletics & Wellness',
    focus: 'Training · Competition · Mindfulness',
    summary: 'Squads mixing varsity conditioning with inclusive wellness circles for balance and readiness.',
    clubCount: 8,
    icon: '🏅',
    gradient: 'linear-gradient(135deg, #991b1b 0%, #dc2626 100%)',
    highlights: ['Cultural Fusion Dance residencies', 'Wellness Ambassador check-ins', 'Performance pods for in-season teams']
  }
]

const fallbackCategory = categories[0]!
const selectedCategoryId = ref(fallbackCategory.id)
const activeCategory = computed(() => categories.find(category => category.id === selectedCategoryId.value) ?? fallbackCategory)
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
      <div class="panel-body">
        <p>{{ activeCategory.summary }}</p>
        <ul>
          <li v-for="highlight in activeCategory.highlights" :key="highlight">
            {{ highlight }}
          </li>
        </ul>
        <button type="button">View sample schedules →</button>
      </div>
    </article>
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

.panel-body {
  padding: clamp(1.5rem, 4vw, 2.5rem);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.panel-body ul {
  list-style: disc;
  padding-left: 1.5rem;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  color: var(--mv-text);
}

.panel-body button {
  align-self: flex-start;
  border-radius: 20px;
  border: 1px solid rgba(250, 204, 21, 0.4);
  background: rgba(250, 204, 21, 0.15);
  color: var(--mv-gold);
  padding: 0.6rem 1.5rem;
  font-weight: 600;
  cursor: pointer;
}

@media (max-width: 640px) {
  .category-panel {
    border-radius: 24px;
  }
}
</style>
