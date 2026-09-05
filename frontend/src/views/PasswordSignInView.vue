<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import { useAuthStore } from '../stores/auth'
import {
  DEFAULT_POST_AUTH_PATH,
  normalizeAuthRedirect,
  resolvePostAuthRoute,
} from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { providers, providersLoading, providersError } = storeToRefs(authStore)
const email = ref('')
const password = ref('')
const submitting = ref(false)
const loginError = ref('')

const passwordLoginEnabled = computed(() =>
  providers.value.some((provider) => provider.id === 'internal'),
)
const redirectTarget = computed(() => normalizeAuthRedirect(route.query.redirect))
const backTarget = computed(() => ({
  path: '/auth',
  query: redirectTarget.value ? { redirect: redirectTarget.value } : undefined,
}))

onMounted(() => {
  authStore.ensureProvidersLoaded()
})

const submit = async () => {
  if (!passwordLoginEnabled.value || submitting.value) return
  submitting.value = true
  loginError.value = ''
  try {
    await authStore.loginWithReviewAccount(email.value, password.value)
    const destination = resolvePostAuthRoute(authStore.currentUser, redirectTarget.value)
    await router.replace(destination ?? DEFAULT_POST_AUTH_PATH)
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Unable to sign in.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="password-page">
    <main class="password-panel">
      <p class="page-label">Password sign in</p>
      <h1>Sign in with password</h1>
      <p class="description">Use the email and password supplied with this app submission.</p>

      <p v-if="providersError" class="alert error" role="alert">{{ providersError }}</p>
      <p v-else-if="providersLoading" class="alert muted">Loading sign-in…</p>

      <form
        v-if="!providersLoading && !providersError && passwordLoginEnabled"
        class="password-form"
        @submit.prevent="submit"
      >
        <label>
          Email
          <input v-model.trim="email" type="email" autocomplete="username" required autofocus />
        </label>
        <label>
          Password
          <input v-model="password" type="password" autocomplete="current-password" required />
        </label>
        <p v-if="loginError" class="alert error" role="alert">{{ loginError }}</p>
        <button class="submit-btn" type="submit" :disabled="submitting">
          {{ submitting ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>

      <p
        v-else-if="!providersLoading && !providersError && !passwordLoginEnabled"
        class="alert muted"
      >
        Password sign-in is not available for this school.
      </p>

      <RouterLink :to="backTarget" class="back-link">Back to sign-in options</RouterLink>
    </main>
  </div>
</template>

<style scoped>
.password-page {
  min-height: 80vh;
  display: grid;
  place-items: center;
  padding: 2rem;
  background:
    radial-gradient(circle at 20% 20%, var(--mv-gold-soft), transparent 55%),
    radial-gradient(circle at 80% 0%, var(--mv-surface-accent), transparent 60%), var(--app-body-bg);
  color: var(--mv-text);
}

.password-panel {
  width: min(100%, 480px);
  padding: clamp(1.5rem, 4vw, 3rem);
  border: 1px solid var(--mv-border);
  border-radius: 32px;
  background: var(--mv-surface-hero-strong);
  box-shadow: var(--mv-shadow-elevated);
}

.page-label {
  margin: 0;
  color: var(--mv-text-dim);
  font-size: 0.75rem;
  letter-spacing: 0.25em;
  text-transform: uppercase;
}

h1 {
  margin: 0.75rem 0 0.5rem;
}

.description {
  margin: 0 0 1.5rem;
  color: var(--mv-text-faint);
  line-height: 1.6;
}

.password-form,
.password-form label {
  display: flex;
  flex-direction: column;
}

.password-form {
  gap: 1rem;
}

.password-form label {
  gap: 0.4rem;
  color: var(--mv-text-soft);
  font-size: 0.9rem;
  font-weight: 600;
}

.password-form input {
  border: 1px solid var(--mv-border);
  border-radius: 14px;
  padding: 0.85rem 0.95rem;
  background: var(--mv-surface-card-strong);
  color: var(--mv-text);
  font-family: inherit;
  /*
   * 16px, not the 0.9rem the label above uses. iOS Safari and WKWebView zoom the page in when a
   * focused field's text is smaller than 16px, and they do not zoom back out on blur -- so on the
   * one page a reviewer has to type on, an inherited label size would leave them stranded at a
   * magnified, horizontally scrolled layout for the rest of the session.
   */
  font-size: 1rem;
  /* Fields are the full panel width; without this the padding above would push them past it. */
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.password-form input:focus {
  outline: 2px solid var(--mv-gold);
  outline-offset: 2px;
}

.submit-btn {
  border: 1px solid var(--mv-border);
  border-radius: 999px;
  padding: 0.85rem 1rem;
  background: var(--mv-surface-card-strong);
  color: var(--mv-text);
  box-shadow: var(--mv-shadow-card);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.submit-btn:disabled {
  cursor: wait;
  opacity: 0.65;
}

.alert {
  margin: 0 0 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 16px;
  font-size: 0.95rem;
}

.alert.error {
  border: 1px solid color-mix(in srgb, var(--mv-status-danger) 45%, transparent);
  background: var(--mv-surface-danger);
}

.alert.muted {
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-soft);
  color: var(--mv-text-faint);
}

.back-link {
  display: inline-block;
  margin-top: 1.5rem;
  color: var(--mv-text-faint);
  font-size: 0.95rem;
  text-decoration: none;
}

.back-link:hover {
  color: var(--mv-gold);
}

/* Matches AuthChoiceView's own narrow-screen panel padding, so moving between the two pages
   does not shift the card's edges. */
@media (max-width: 480px) {
  .password-page {
    padding: 1rem;
  }

  .password-panel {
    padding: 2rem 1.5rem;
    border-radius: 28px;
  }
}
</style>
