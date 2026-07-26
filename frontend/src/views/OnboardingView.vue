<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { schoolTemplate } from '../config/schoolTemplate'
import { useAuthStore } from '../stores/auth'
import { updateGraduationYear } from '../services/userService'
import { sanitizeAuthRedirectTarget } from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const brandName = schoolTemplate.brandName

const saving = ref(false)
const error = ref('')
const graduationYear = ref<number | null>(authStore.currentUser?.graduationYear ?? null)

const yearOptions = computed(() => {
  const currentYear = new Date().getFullYear()
  const years: number[] = []
  for (let i = 0; i < 4; i++) {
    years.push(currentYear + i)
  }
  return years
})

const canSave = computed(() => graduationYear.value !== null && graduationYear.value > 0)

// Deliberately bypasses resolvePostAuthRoute: at this point graduationYear is
// still null, so the full resolver would just send us back to /onboarding.
const resolveRedirectTarget = () => sanitizeAuthRedirectTarget(route.query.redirect)

const handleSave = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  try {
    await updateGraduationYear(graduationYear.value!)
    await authStore.refreshUser()
    router.replace(resolveRedirectTarget())
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to save profile'
  } finally {
    saving.value = false
  }
}

// Redirect already-onboarded users away from this page
onMounted(() => {
  if (authStore.currentUser?.graduationYear != null) {
    router.replace(resolveRedirectTarget())
  }
})

const handleSkip = () => {
  router.replace(resolveRedirectTarget())
}
</script>

<template>
  <div class="onboarding-page">
    <div class="onboarding-card">
      <h1>Welcome to {{ brandName }}!</h1>
      <p class="subtitle">Set up your profile to get the most out of the club directory.</p>

      <form @submit.prevent="handleSave" class="onboarding-form">
        <label>
          <span>Graduation year <em>(required)</em></span>
          <select v-model="graduationYear">
            <option :value="null" disabled>Select year…</option>
            <option v-for="year in yearOptions" :key="year" :value="year">{{ year }}</option>
          </select>
        </label>

        <p class="interest-note">
          After setup, you can browse clubs by category and join the ones that match your interests.
        </p>

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
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(2rem, 5vw, 4rem) var(--page-padding-inline);
  background: var(--app-body-bg);
  color: var(--mv-text);
}

.onboarding-card {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: clamp(2rem, 4vw, 3rem);
  border-radius: var(--mv-radius);
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  box-shadow: var(--mv-shadow-elevated);
  max-width: 520px;
  width: 100%;
}

.onboarding-card h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--mv-text);
}

.subtitle {
  margin: 0;
  color: var(--mv-text-muted);
  line-height: 1.6;
}

.interest-note {
  color: var(--mv-text-faint);
  font-size: 0.9rem;
  line-height: 1.6;
  margin: 0;
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
  color: var(--mv-status-danger);
}

.onboarding-form select {
  padding: 0.6rem 0.85rem;
  border-radius: 12px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card-strong);
  color: var(--mv-text);
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
}

.onboarding-form select:focus {
  border-color: var(--mv-border-strong);
  box-shadow: 0 0 0 3px var(--mv-gold-soft);
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

.error-msg {
  padding: 0.6rem 1rem;
  border-radius: 12px;
  background: var(--mv-surface-danger);
  border: 1px solid color-mix(in srgb, var(--mv-status-danger) 45%, transparent);
  color: var(--mv-status-danger);
  font-size: 0.9rem;
}

.onboarding-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.ghost-btn,
.primary-btn {
  border-radius: 999px;
  padding: 0.62rem 1.15rem;
  border: 1px solid var(--mv-ghost-border);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    opacity 0.2s ease;
}

.ghost-btn {
  background: transparent;
  color: var(--mv-ghost-text);
}

.ghost-btn:hover {
  background: var(--mv-surface-accent);
  border-color: var(--mv-border-strong);
}

.primary-btn {
  border-color: var(--mv-primary-bg);
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  box-shadow: var(--mv-primary-shadow);
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  box-shadow: none;
}

@media (max-width: 520px) {
  .onboarding-actions {
    flex-direction: column-reverse;
  }

  .ghost-btn,
  .primary-btn {
    width: 100%;
  }
}
</style>
