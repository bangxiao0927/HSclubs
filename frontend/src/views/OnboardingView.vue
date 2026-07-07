<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { updateGraduationYear } from '../services/userService'
import { clubCategoryOptions } from '../utils/clubCategories'

const router = useRouter()
const authStore = useAuthStore()

const saving = ref(false)
const error = ref('')
const graduationYear = ref<number | null>(authStore.currentUser?.graduationYear ?? null)
const selectedInterests = ref<string[]>([])

const yearOptions = computed(() => {
  const currentYear = new Date().getFullYear()
  const years: number[] = []
  for (let i = 0; i < 5; i++) {
    years.push(currentYear + i)
  }
  return years
})

const toggleInterest = (title: string) => {
  const idx = selectedInterests.value.indexOf(title)
  if (idx >= 0) {
    selectedInterests.value.splice(idx, 1)
  } else {
    selectedInterests.value.push(title)
  }
}

const canSave = computed(() => graduationYear.value !== null && graduationYear.value > 0)

const handleSave = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  try {
    await updateGraduationYear(graduationYear.value!)
    await authStore.refreshUser()
    router.replace({ path: '/profile' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to save profile'
  } finally {
    saving.value = false
  }
}

const handleSkip = () => {
  router.replace({ path: '/profile' })
}
</script>

<template>
  <div class="onboarding-page">
    <div class="onboarding-card">
      <h1>Welcome to HS Clubs!</h1>
      <p class="subtitle">Set up your profile to get the most out of the club directory.</p>

      <form @submit.prevent="handleSave" class="onboarding-form">
        <label>
          <span>Graduation year <em>(required)</em></span>
          <select v-model="graduationYear">
            <option :value="null" disabled>Select year…</option>
            <option v-for="year in yearOptions" :key="year" :value="year">{{ year }}</option>
          </select>
        </label>

        <fieldset>
          <legend>Interests (optional) — helps us recommend clubs</legend>
          <div class="interest-chips">
            <button
              v-for="cat in clubCategoryOptions"
              :key="cat.title"
              type="button"
              class="chip"
              :class="{ active: selectedInterests.includes(cat.title) }"
              @click="toggleInterest(cat.title)"
            >
              <span class="chip-icon">{{ cat.icon }}</span>
              {{ cat.title }}
            </button>
          </div>
        </fieldset>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <div class="onboarding-actions">
          <button type="button" class="ghost-btn" @click="handleSkip">Skip for now</button>
          <button type="submit" class="primary-btn" :disabled="!canSave || saving">
            {{ saving ? 'Saving…' : 'Continue' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.onboarding-page {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: radial-gradient(circle at 20% 20%, rgba(253, 224, 71, 0.15), transparent 55%),
    radial-gradient(circle at 80% 0%, rgba(59, 130, 246, 0.2), transparent 60%),
    #06070b;
  color: #fefce8;
}

.onboarding-card {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: clamp(2rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  background: linear-gradient(120deg, rgba(250, 204, 21, 0.18), rgba(5, 5, 5, 0.95));
  max-width: 520px;
  width: 100%;
}

.onboarding-card h1 {
  margin: 0;
  font-size: 1.5rem;
}

.subtitle {
  margin: 0;
  color: rgba(254, 252, 232, 0.75);
  line-height: 1.6;
}

.onboarding-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.onboarding-form label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.onboarding-form label span em {
  font-style: normal;
  color: #f87171;
}

.onboarding-form select {
  padding: 0.6rem 0.85rem;
  border-radius: 12px;
  border: 1px solid rgba(254, 252, 232, 0.15);
  background: rgba(10, 10, 20, 0.6);
  color: #fefce8;
  font-size: 0.95rem;
  font-family: inherit;
}

fieldset {
  border: none;
  padding: 0;
  margin: 0;
}

legend {
  font-size: 0.9rem;
  margin-bottom: 0.6rem;
}

.interest-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.85rem;
  border-radius: 20px;
  border: 1px solid rgba(254, 252, 232, 0.15);
  background: rgba(10, 10, 20, 0.5);
  color: rgba(254, 252, 232, 0.8);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.chip:hover {
  border-color: rgba(250, 204, 21, 0.4);
}

.chip.active {
  border-color: #fde047;
  background: rgba(250, 204, 21, 0.15);
  color: #fde047;
}

.chip-icon {
  font-size: 1rem;
}

.error-msg {
  padding: 0.6rem 1rem;
  border-radius: 12px;
  background: rgba(248, 113, 113, 0.15);
  border: 1px solid rgba(248, 113, 113, 0.3);
  color: #f87171;
  font-size: 0.9rem;
}

.onboarding-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}
</style>
