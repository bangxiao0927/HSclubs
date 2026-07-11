<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from '../stores/auth'
import BackButton from '../components/BackButton.vue'

const route = useRoute()
const authStore = useAuthStore()
const { providers, providersLoading, providersError } = storeToRefs(authStore)
const acceptedTerms = ref(false)
const termsError = ref('')

onMounted(() => {
  authStore.ensureProvidersLoaded()
})

const intentLabel = computed(() => (route.query.intent === 'register' ? 'Create account' : 'Sign in'))

const routeError = computed(() => {
  return typeof route.query.error === 'string' ? 'We could not complete your sign in. Please try again.' : ''
})

const handleProviderLogin = (providerId: string) => {
  if (!acceptedTerms.value) {
    termsError.value = 'Please accept the Terms of Use before continuing.'
    return
  }
  termsError.value = ''
  authStore.beginLogin(providerId)
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <p class="page-label">{{ intentLabel }}</p>
      <h1>Continue with your school account</h1>
      <p class="description">
        Use OAuth2 to sign in safely with your school-provided Google account. We only request basic profile details so you can
        access HS Clubs across devices.
      </p>
      <div class="alerts">
        <p v-if="routeError" class="alert error">{{ routeError }}</p>
        <p v-if="providersError" class="alert error">{{ providersError }}</p>
        <p v-if="termsError" class="alert error">{{ termsError }}</p>
        <p v-else-if="providersLoading" class="alert muted">Loading sign-in options…</p>
      </div>
      <label class="terms-check">
        <input v-model="acceptedTerms" type="checkbox" @change="termsError = ''" />
        <span>
          I agree to the
          <RouterLink to="/terms" target="_blank">Terms of Use</RouterLink>
          and
          <RouterLink to="/privacy" target="_blank">Privacy Policy</RouterLink>.
        </span>
      </label>
      <div v-if="!providersLoading && !providersError" class="provider-list">
        <button
          v-for="provider in providers"
          :key="provider.id"
          class="provider-btn"
          type="button"
          :disabled="!acceptedTerms"
          @click="handleProviderLogin(provider.id)"
        >
          <span class="provider-icon" aria-hidden="true">{{ provider.name.charAt(0) }}</span>
          Sign in with {{ provider.name }}
        </button>
        <p v-if="providers.length === 0" class="alert muted">No OAuth providers are configured yet.</p>
      </div>
      <BackButton>Back to club catalog</BackButton>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background:
    radial-gradient(circle at 20% 20%, var(--mv-gold-soft), transparent 55%),
    radial-gradient(circle at 80% 0%, var(--mv-surface-accent), transparent 60%),
    var(--app-body-bg);
  color: var(--mv-text);
}

.login-panel {
  display: flex;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--mv-shadow-elevated);
}

.page-label {
  text-transform: uppercase;
  letter-spacing: 0.3em;
  font-size: 0.75rem;
  color: var(--mv-text-dim);
  margin: 0;
}

.login-panel h1 {
  margin: 1rem 0 0.5rem;
  font-size: clamp(1.6rem, 4vw, 2.3rem);
}

.description {
  margin: 0 auto 1.5rem;
  max-width: 32ch;
  color: var(--mv-text-faint);
  line-height: 1.6;
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
}

.provider-btn {
  width: 100%;
  border: 1px solid var(--mv-border);
  border-radius: 999px;
  padding: 0.85rem 1rem;
  font-size: 1rem;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  cursor: pointer;
  color: var(--mv-text);
  background: var(--mv-surface-card-strong);
  box-shadow: var(--mv-shadow-card);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.provider-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--mv-shadow-elevated);
}

.provider-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.terms-check {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  color: var(--mv-text-soft);
  font-size: 0.95rem;
  line-height: 1.5;
}

.terms-check input {
  margin-top: 0.25rem;
  flex: 0 0 auto;
}

.terms-check a {
  color: var(--mv-gold);
  font-weight: 600;
}

.provider-icon {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  background: var(--mv-surface-soft);
  border: 1px solid var(--mv-border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #ea4335;
  font-family: 'Inter', 'Segoe UI', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
}

.alerts {
  width: 100%;
}

.alert {
  margin: 0 0 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  font-size: 0.95rem;
}

.alert.error {
  background: var(--mv-surface-danger);
  border: 1px solid color-mix(in srgb, var(--mv-status-danger) 45%, transparent);
  color: var(--mv-text);
}

.alert.muted {
  background: var(--mv-surface-soft);
  border: 1px solid var(--mv-border);
  color: var(--mv-text-faint);
}

.back-link {
  display: inline-block;
  margin-top: 1.5rem;
  color: var(--mv-text-faint);
  text-decoration: none;
  font-size: 0.95rem;
  transition: color 0.2s ease;
}

.back-link:hover {
  color: var(--mv-gold);
}

@media (max-width: 480px) {
  .login-panel {
    padding: 2rem 1.5rem;
  }
}
</style>
