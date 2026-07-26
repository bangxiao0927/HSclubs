<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { schoolTemplate } from '../config/schoolTemplate'
import { useAuthStore } from '../stores/auth'
import { buildApiUrl } from '../services/httpClient'
import { DEFAULT_POST_AUTH_PATH, resolvePostAuthRoute } from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const brandName = schoolTemplate.brandName
const agreed = ref(false)
const loading = ref(false)
const error = ref('')

const handleAccept = async () => {
  if (!agreed.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(buildApiUrl('/api/auth/accept-terms'), {
      method: 'POST',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error('Failed to record acceptance')
    }
    await authStore.refreshUser()
    const destination = resolvePostAuthRoute(authStore.currentUser, route.query.redirect)
    router.replace(destination ?? DEFAULT_POST_AUTH_PATH)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Something went wrong'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="accept-terms page-shell">
    <header class="terms-hero">
      <p class="section-label">Welcome</p>
      <h1>Before you continue</h1>
      <p>Please review and accept our terms to start using {{ brandName }}.</p>
    </header>

    <div class="terms-summary">
      <div class="summary-card">
        <h2>Terms of Use</h2>
        <ul>
          <li>Use a valid school Google account</li>
          <li>Provide accurate information</li>
          <li>Respect other community members</li>
          <li>Club leaders are responsible for their listings</li>
        </ul>
        <RouterLink to="/terms" target="_blank" class="read-link">Read full terms →</RouterLink>
      </div>

      <div class="summary-card">
        <h2>Privacy Policy</h2>
        <ul>
          <li>We collect your name, email, and profile picture via Google</li>
          <li>Your data stays within the {{ brandName }} platform</li>
          <li>We do not sell or share your personal information</li>
          <li>You can request account deletion anytime</li>
        </ul>
        <RouterLink to="/privacy" target="_blank" class="read-link">Read full policy →</RouterLink>
      </div>
    </div>

    <form class="accept-form" @submit.prevent="handleAccept">
      <label class="checkbox-label">
        <input v-model="agreed" type="checkbox" />
        <span>I have read and agree to the Terms of Use and Privacy Policy</span>
      </label>

      <p v-if="error" class="form-error">{{ error }}</p>

      <button type="submit" class="btn primary" :disabled="!agreed || loading">
        {{ loading ? 'Saving…' : `Continue to ${brandName}` }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.accept-terms {
  padding-block: clamp(2rem, 5vw, 4rem);
  max-width: 680px;
}

.terms-hero {
  margin-bottom: 2.5rem;
  text-align: center;
}

.terms-hero h1 {
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  font-weight: 700;
  color: var(--mv-text);
  margin: 0.5rem 0;
}

.terms-hero p {
  color: var(--mv-text-muted);
}

.terms-summary {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 2rem;
}

.summary-card {
  padding: 1.5rem;
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
}

.summary-card h2 {
  font-size: 1.1rem;
  margin: 0 0 0.75rem;
  color: var(--mv-text);
}

.summary-card ul {
  margin: 0;
  padding-left: 1.25rem;
  color: var(--mv-text-muted);
  font-size: 0.9rem;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.read-link {
  display: inline-block;
  margin-top: 0.75rem;
  font-size: 0.9rem;
  color: var(--mv-gold);
}

.accept-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.checkbox-label {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  font-size: 0.95rem;
  color: var(--mv-text);
  cursor: pointer;
}

.checkbox-label input {
  margin-top: 0.25rem;
}

.form-error {
  color: var(--mv-status-danger);
  font-size: 0.9rem;
  margin: 0;
}

.btn.primary {
  padding: 0.7rem 1.5rem;
  border-radius: 999px;
  background: var(--mv-primary-bg);
  color: var(--mv-primary-text);
  border: none;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  align-self: flex-start;
}

.btn.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
