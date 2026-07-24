<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchAllClubs } from '../services/clubService'
import type { Club } from '../types/club'
import { clubCategoryOptions } from '../utils/clubCategories'
import { clubImage } from '../utils/clubImages'

type QuizOption = {
  label: string
  description: string
  scores: Record<string, number>
}

type QuizQuestion = {
  prompt: string
  helper: string
  options: QuizOption[]
}

const questions: QuizQuestion[] = [
  {
    prompt: 'Which activity sounds most exciting?',
    helper: 'Choose the option you would be happiest to spend an afternoon doing.',
    options: [
      {
        label: 'Build and experiment',
        description: 'Code, engineer, research, or solve a technical problem.',
        scores: { 'STEM & Innovation': 3, 'Competition & Strategy': 1 },
      },
      {
        label: 'Create and perform',
        description: 'Make art, music, writing, film, or live performances.',
        scores: { 'Creative Arts & Media': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Serve and lead',
        description: 'Support a cause, organize people, or improve the community.',
        scores: { 'Service & Leadership': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Move and recharge',
        description: 'Play a sport, stay active, or support personal wellbeing.',
        scores: { 'Wellness & Athletics': 3, 'Competition & Strategy': 1 },
      },
      {
        label: 'Compete and strategize',
        description: 'Debate, play games, or prepare for an academic competition.',
        scores: { 'Competition & Strategy': 3, 'STEM & Innovation': 1 },
      },
      {
        label: 'Connect through identity',
        description: 'Share traditions, languages, experiences, and community.',
        scores: { 'Culture & Identity': 3, 'Service & Leadership': 1 },
      },
    ],
  },
  {
    prompt: 'What do you most want from a club?',
    helper: 'Think about what would make the experience worthwhile for you.',
    options: [
      {
        label: 'Practical skills',
        description: 'Learn tools and techniques I can use in future projects.',
        scores: { 'STEM & Innovation': 3, 'Creative Arts & Media': 1 },
      },
      {
        label: 'A creative outlet',
        description: 'Express ideas and make something I am proud to share.',
        scores: { 'Creative Arts & Media': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Meaningful impact',
        description: 'Help others and make a visible difference at school or beyond.',
        scores: { 'Service & Leadership': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Belonging and connection',
        description: 'Meet people who share my background, interests, or experiences.',
        scores: { 'Culture & Identity': 3, 'Service & Leadership': 1 },
      },
      {
        label: 'Healthy balance',
        description: 'Reduce stress, move more, and make time for wellbeing.',
        scores: { 'Wellness & Athletics': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'A challenge',
        description: 'Test myself, improve through practice, and work toward a goal.',
        scores: { 'Competition & Strategy': 3, 'STEM & Innovation': 1 },
      },
    ],
  },
  {
    prompt: 'Which club environment fits you best?',
    helper:
      'There is no wrong answer. Pick the setting where you would feel comfortable participating.',
    options: [
      {
        label: 'A focused workshop',
        description: 'We learn by testing ideas and improving a project together.',
        scores: { 'STEM & Innovation': 3, 'Creative Arts & Media': 1 },
      },
      {
        label: 'An open studio',
        description: 'Everyone brings a perspective and experiments with new forms.',
        scores: { 'Creative Arts & Media': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'A community team',
        description: 'We organize, collaborate, and take action around shared goals.',
        scores: { 'Service & Leadership': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'A welcoming social space',
        description: 'Conversation and shared experiences help people feel included.',
        scores: { 'Culture & Identity': 3, 'Service & Leadership': 1 },
      },
      {
        label: 'An active practice',
        description: 'We learn by moving, training, and encouraging one another.',
        scores: { 'Wellness & Athletics': 3, 'Competition & Strategy': 1 },
      },
      {
        label: 'A prepared team',
        description: 'We practice with purpose and enjoy measuring our progress.',
        scores: { 'Competition & Strategy': 3, 'Wellness & Athletics': 1 },
      },
    ],
  },
  {
    prompt: 'How would you like to contribute?',
    helper: 'Choose the role you would be most likely to take once you feel settled in.',
    options: [
      {
        label: 'Solve the hard problem',
        description: 'Research options, understand the details, and find a working answer.',
        scores: { 'STEM & Innovation': 3, 'Competition & Strategy': 1 },
      },
      {
        label: 'Shape the story',
        description: 'Design, write, perform, or help an idea connect with an audience.',
        scores: { 'Creative Arts & Media': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Bring people together',
        description: 'Plan events, welcome members, and help the group move forward.',
        scores: { 'Service & Leadership': 3, 'Culture & Identity': 1 },
      },
      {
        label: 'Share my perspective',
        description: 'Start conversations and help others understand different experiences.',
        scores: { 'Culture & Identity': 3, 'Service & Leadership': 1 },
      },
      {
        label: 'Keep the team motivated',
        description: 'Show up consistently, encourage others, and build healthy habits.',
        scores: { 'Wellness & Athletics': 3, 'Service & Leadership': 1 },
      },
      {
        label: 'Prepare the strategy',
        description: 'Study the challenge, practice, and help the team perform its best.',
        scores: { 'Competition & Strategy': 3, 'STEM & Innovation': 1 },
      },
    ],
  },
]

const clubs = ref<Club[]>([])
const loading = ref(true)
const error = ref('')
const currentQuestionIndex = ref(0)
const answers = ref<(number | null)[]>(questions.map(() => null))
const showResults = ref(false)

const currentQuestion = computed(() => questions[currentQuestionIndex.value]!)
const selectedOptionIndex = computed(() => answers.value[currentQuestionIndex.value])
const completedAnswerCount = computed(
  () => answers.value.filter((answer) => answer !== null).length,
)
const progress = computed(() => (completedAnswerCount.value / questions.length) * 100)

const rankedCategories = computed(() => {
  const scores = new Map(clubCategoryOptions.map((category) => [category.title, 0]))

  answers.value.forEach((answer, questionIndex) => {
    if (answer === null) return
    const option = questions[questionIndex]?.options[answer]
    Object.entries(option?.scores ?? {}).forEach(([category, score]) => {
      scores.set(category, (scores.get(category) ?? 0) + score)
    })
  })

  return clubCategoryOptions
    .map((category, index) => ({ ...category, score: scores.get(category.title) ?? 0, index }))
    .sort((a, b) => b.score - a.score || a.index - b.index)
})

const topCategories = computed(() => rankedCategories.value.slice(0, 3))
const recommendedClubs = computed(() => {
  const categoryQueues = topCategories.value.map((category) => ({
    title: category.title,
    clubs: clubs.value
      .filter((club) => club.category === category.title)
      .sort((a, b) => {
        const memberDelta = (b.memberCount ?? 0) - (a.memberCount ?? 0)
        return memberDelta !== 0 ? memberDelta : a.name.localeCompare(b.name)
      }),
    nextIndex: 0,
  }))
  const matches: Club[] = []

  while (matches.length < 12) {
    let addedClub = false
    categoryQueues.forEach((queue) => {
      const club = queue.clubs[queue.nextIndex]
      if (club && matches.length < 12) {
        matches.push(club)
        queue.nextIndex++
        addedClub = true
      }
    })
    if (!addedClub) break
  }

  return matches
})

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

const selectOption = (optionIndex: number) => {
  answers.value[currentQuestionIndex.value] = optionIndex
}

const goBack = () => {
  if (currentQuestionIndex.value > 0) currentQuestionIndex.value--
}

const continueQuiz = () => {
  if (selectedOptionIndex.value === null) return
  if (currentQuestionIndex.value < questions.length - 1) {
    currentQuestionIndex.value++
    return
  }
  showResults.value = true
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const restartQuiz = () => {
  answers.value = questions.map(() => null)
  currentQuestionIndex.value = 0
  showResults.value = false
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(loadClubs)
</script>

<template>
  <main class="quiz-page page-shell">
    <template v-if="!showResults">
      <header class="quiz-header">
        <p class="section-label">Club match quiz</p>
        <h1>Find clubs that fit you</h1>
        <p>
          Answer four quick questions and we will match your interests with clubs at your school.
        </p>
      </header>

      <section class="quiz-card" aria-labelledby="quiz-question">
        <div class="progress-row">
          <span>Question {{ currentQuestionIndex + 1 }} of {{ questions.length }}</span>
          <span>{{ Math.round(progress) }}% complete</span>
        </div>
        <div class="progress-track" aria-hidden="true">
          <span :style="{ width: `${progress}%` }"></span>
        </div>

        <div class="question-copy">
          <h2 id="quiz-question">{{ currentQuestion.prompt }}</h2>
          <p>{{ currentQuestion.helper }}</p>
        </div>

        <fieldset class="answer-fieldset">
          <legend class="answer-legend">{{ currentQuestion.prompt }}</legend>
          <div class="answer-grid">
            <label
              v-for="(option, optionIndex) in currentQuestion.options"
              :key="option.label"
              class="answer-card"
              :class="{ selected: selectedOptionIndex === optionIndex }"
            >
              <input
                class="answer-input"
                type="radio"
                :name="`quiz-question-${currentQuestionIndex}`"
                :value="optionIndex"
                :checked="selectedOptionIndex === optionIndex"
                @change="selectOption(optionIndex)"
              />
              <span class="answer-marker">{{
                selectedOptionIndex === optionIndex ? '✓' : optionIndex + 1
              }}</span>
              <span>
                <strong>{{ option.label }}</strong>
                <small>{{ option.description }}</small>
              </span>
            </label>
          </div>
        </fieldset>

        <footer class="quiz-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="currentQuestionIndex === 0"
            @click="goBack"
          >
            Back
          </button>
          <button
            type="button"
            class="primary-button"
            :disabled="selectedOptionIndex === null"
            @click="continueQuiz"
          >
            {{ currentQuestionIndex === questions.length - 1 ? 'See my matches' : 'Next question' }}
          </button>
        </footer>
      </section>
    </template>

    <template v-else>
      <header class="results-header">
        <div>
          <p class="section-label">Your results</p>
          <h1>Your best club matches</h1>
          <p>These recommendations are based on the interests and environment you selected.</p>
        </div>
        <button type="button" class="secondary-button" @click="restartQuiz">Retake quiz</button>
      </header>

      <section class="match-summary" aria-label="Top category matches">
        <article
          v-for="(category, index) in topCategories"
          :key="category.title"
          class="match-category"
        >
          <span class="match-rank">#{{ index + 1 }} match</span>
          <span class="match-icon" aria-hidden="true">{{ category.icon }}</span>
          <h2>{{ category.title }}</h2>
          <p>{{ category.description }}</p>
        </article>
      </section>

      <section class="club-results" aria-labelledby="recommended-clubs-heading">
        <div class="results-title-row">
          <div>
            <p class="section-label">Recommended clubs</p>
            <h2 id="recommended-clubs-heading">Start exploring</h2>
          </div>
          <RouterLink to="/about" class="text-link">Browse every category</RouterLink>
        </div>

        <div v-if="loading" class="status-card">Finding clubs for you…</div>
        <div v-else-if="error" class="status-card error">
          <p>{{ error }}</p>
          <button type="button" class="secondary-button" @click="loadClubs">Try again</button>
        </div>
        <div v-else-if="recommendedClubs.length" class="club-grid">
          <RouterLink
            v-for="club in recommendedClubs"
            :key="club.id"
            :to="`/clubs/${club.id}`"
            class="club-card"
          >
            <img :src="clubImage(club)" :alt="`${club.name} avatar`" loading="lazy" />
            <div>
              <span>{{ club.category }}</span>
              <h3>{{ club.name }}</h3>
              <p>{{ club.description }}</p>
              <small
                >{{ club.memberCount }} members ·
                {{ club.meetingSchedule || 'Schedule TBD' }}</small
              >
            </div>
          </RouterLink>
        </div>
        <div v-else class="status-card">
          No matching clubs are available yet. Try browsing all categories instead.
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.quiz-page {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding-block: clamp(2rem, 5vw, 4rem);
}

.quiz-header,
.results-header {
  max-width: 880px;
}

.quiz-header h1,
.results-header h1 {
  margin: 0.5rem 0 0.75rem;
  font-size: clamp(2rem, 4vw, 3.2rem);
}

.quiz-header > p:last-child,
.results-header > div > p:last-child {
  max-width: 720px;
  color: var(--mv-text-muted);
}

.quiz-card {
  width: min(900px, 100%);
  padding: clamp(1.25rem, 4vw, 2.5rem);
  border: 1px solid var(--mv-border);
  border-radius: 30px;
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-elevated);
}

.progress-row,
.quiz-actions,
.results-header,
.results-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.progress-row {
  color: var(--mv-text-faint);
  font-size: 0.85rem;
  font-weight: 600;
}

.progress-track {
  height: 7px;
  margin-top: 0.65rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--mv-surface-soft);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--mv-gold);
  transition: width 0.25s ease;
}

.question-copy {
  margin: 2rem 0 1.25rem;
}

.question-copy h2 {
  margin: 0 0 0.5rem;
  font-size: clamp(1.45rem, 3vw, 2rem);
}

.question-copy p {
  color: var(--mv-text-muted);
}

.answer-fieldset {
  min-width: 0;
  padding: 0;
  border: 0;
}

.answer-legend,
.answer-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.answer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.85rem;
}

.answer-card {
  display: flex;
  align-items: flex-start;
  gap: 0.85rem;
  min-height: 112px;
  padding: 1rem;
  border: 1px solid var(--mv-border);
  border-radius: 18px;
  background: var(--mv-surface-muted);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
}

.answer-card:hover,
.answer-card.selected {
  border-color: var(--mv-gold);
  background: var(--mv-surface-accent);
  transform: translateY(-1px);
}

.answer-input:focus-visible + .answer-marker {
  outline: 2px solid var(--mv-gold);
  outline-offset: 3px;
}

.answer-marker {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border: 1px solid var(--mv-border-strong);
  border-radius: 50%;
  color: var(--mv-gold);
  font-weight: 700;
}

.answer-card strong,
.answer-card small {
  display: block;
}

.answer-card small {
  margin-top: 0.35rem;
  color: var(--mv-text-faint);
  font-size: 0.85rem;
  line-height: 1.45;
}

.quiz-actions {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--mv-border);
}

.primary-button,
.secondary-button,
.text-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0.7rem 1.1rem;
  border-radius: 999px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  border: 1px solid var(--mv-primary-bg);
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
}

.secondary-button,
.text-link {
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-muted);
  color: var(--mv-text);
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.match-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.match-category {
  padding: 1.4rem;
  border: 1px solid var(--mv-border);
  border-radius: 24px;
  background: var(--mv-surface-card);
  box-shadow: var(--mv-shadow-card);
}

.match-category:first-child {
  border-color: var(--mv-border-strong);
  background: var(--mv-surface-accent);
}

.match-rank {
  color: var(--mv-gold);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.match-icon {
  display: block;
  margin: 1.2rem 0 0.65rem;
  font-size: 1.7rem;
}

.match-category h2 {
  margin: 0;
  font-size: 1.15rem;
}

.match-category p {
  margin-top: 0.5rem;
  color: var(--mv-text-faint);
  font-size: 0.9rem;
}

.club-results {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.results-title-row h2 {
  margin: 0.4rem 0 0;
}

.club-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}

.club-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 1rem;
  padding: 1.15rem;
  border: 1px solid var(--mv-border);
  border-radius: 20px;
  background: var(--mv-surface-card);
  color: inherit;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;
}

.club-card:hover,
.club-card:focus-visible {
  border-color: var(--mv-gold);
  transform: translateY(-2px);
}

.club-card img {
  width: 72px;
  height: 72px;
  border-radius: 18px;
  object-fit: cover;
}

.club-card span {
  color: var(--mv-gold);
  font-size: 0.75rem;
  font-weight: 600;
}

.club-card h3 {
  margin: 0.2rem 0 0.4rem;
  font-size: 1rem;
}

.club-card p {
  display: -webkit-box;
  margin: 0 0 0.65rem;
  overflow: hidden;
  color: var(--mv-text-faint);
  font-size: 0.85rem;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.club-card small {
  color: var(--mv-text-muted);
}

.status-card {
  padding: 1.25rem;
  border: 1px solid var(--mv-border);
  border-radius: 20px;
  background: var(--mv-surface-card);
}

.status-card.error {
  color: var(--mv-status-danger);
}

@media (min-width: 761px) {
  .quiz-header,
  .quiz-card {
    align-self: center;
  }

  .quiz-header {
    width: min(900px, 100%);
    text-align: center;
  }

  .quiz-header > p:last-child {
    margin-inline: auto;
  }
}

@media (max-width: 760px) {
  .answer-grid,
  .match-summary {
    grid-template-columns: 1fr;
  }

  .answer-card {
    min-height: 0;
  }

  .results-header,
  .results-title-row {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .quiz-page {
    gap: 1.5rem;
    padding-block: 1.5rem 2.5rem;
  }

  .quiz-card {
    padding: 1rem;
    border-radius: 22px;
  }

  .question-copy {
    margin-block: 1.5rem 1rem;
  }

  .progress-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.2rem;
  }

  .quiz-actions .primary-button {
    flex: 1;
  }

  .club-grid {
    grid-template-columns: 1fr;
  }

  .results-header > .secondary-button,
  .results-title-row .text-link {
    width: 100%;
  }
}

@media (max-width: 360px) {
  .club-card {
    grid-template-columns: 60px minmax(0, 1fr);
    gap: 0.75rem;
    padding: 0.9rem;
  }

  .club-card img {
    width: 60px;
    height: 60px;
    border-radius: 15px;
  }
}
</style>
