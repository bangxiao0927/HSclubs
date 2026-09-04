<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { schoolTemplate } from '../config/schoolTemplate'
import { useAuthStore } from '../stores/auth'
import BackButton from '../components/BackButton.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const brandName = schoolTemplate.brandName
const { providers, providersLoading, providersError } = storeToRefs(authStore)

onMounted(() => {
  authStore.ensureProvidersLoaded()
})

const intentLabel = computed(() =>
  route.query.intent === 'register' ? 'Create account' : 'Sign in',
)

// The backend's OAuth2 failure handler passes its own codes for the school's sign-in
// restrictions, so a student turned away by them is told why instead of being invited to retry
// something that can never succeed. Anything else stays the generic retry message.
// A Map, not an object literal: the code comes straight from the URL, so a plain object would
// resolve `?error=toString` (or constructor, valueOf, ...) through the prototype chain and
// render a function body instead of falling back to the generic message.
const authErrorMessages = new Map<string, string>([
  [
    'email_domain_not_allowed',
    'That account is not allowed to sign in here. Please use your school account.',
  ],
  [
    'email_not_verified',
    'That account\u2019s email address is not verified with its provider, so it cannot be used to sign in.',
  ],
])

const routeError = computed(() => {
  const code = route.query.error
  if (typeof code !== 'string') {
    return ''
  }
  return authErrorMessages.get(code) ?? 'We could not complete your sign in. Please try again.'
})

const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' ? redirect : null
})

const reviewAccountEnabled = computed(() =>
  providers.value.some((provider) => provider.id === 'internal'),
)
const oauthProviders = computed(() =>
  providers.value.filter((provider) => provider.id !== 'internal'),
)

const handleProviderLogin = (providerId: string) => {
  authStore.beginLogin(providerId, redirectTarget.value)
}

const handlePasswordLogin = () => {
  const query = redirectTarget.value ? { redirect: redirectTarget.value } : undefined
  router.push({ path: '/auth/password', query })
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <p class="page-label">{{ intentLabel }}</p>
      <h1>Continue with your school account</h1>
      <p class="description">
        Use your school-provided Google account to access {{ brandName }} across devices.
      </p>
      <div class="alerts">
        <p v-if="routeError" class="alert error">{{ routeError }}</p>
        <p v-if="providersError" class="alert error">{{ providersError }}</p>
        <p v-else-if="providersLoading" class="alert muted">Loading sign-in options…</p>
      </div>
      <p class="terms-notice">
        By continuing you agree to our
        <RouterLink to="/terms" target="_blank">Terms of Use</RouterLink>
        and
        <RouterLink to="/privacy" target="_blank">Privacy Policy</RouterLink>.
      </p>
      <div v-if="!providersLoading && !providersError" class="provider-list">
        <button
          v-for="provider in oauthProviders"
          :key="provider.id"
          class="provider-btn"
          type="button"
          @click="handleProviderLogin(provider.id)"
        >
          <span class="provider-icon" aria-hidden="true">{{ provider.name.charAt(0) }}</span>
          Sign in with {{ provider.name }}
        </button>
        <button
          v-if="reviewAccountEnabled"
          class="provider-btn"
          type="button"
          @click="handlePasswordLogin"
        >
          <span class="provider-icon password-icon" aria-hidden="true">P</span>
          Sign in with password
        </button>
        <p v-if="providers.length === 0" class="alert muted">
          No OAuth providers are configured yet.
        </p>
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
    radial-gradient(circle at 80% 0%, var(--mv-surface-accent), transparent 60%), var(--app-body-bg);
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
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.provider-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--mv-shadow-elevated);
}

.password-icon {
  color: var(--mv-gold);
}

.terms-notice {
  width: 100%;
  margin: 0;
  color: var(--mv-text-soft);
  font-size: 0.95rem;
  line-height: 1.5;
}

.terms-notice a {
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
  font-family:
    'Inter',
    'Segoe UI',
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
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
