<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import {
  clearPendingAuthRedirect,
  consumePendingAuthRedirect,
  DEFAULT_POST_AUTH_PATH,
  normalizeAuthRedirect,
  resolvePostAuthRoute,
} from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const resolveRedirectTarget = () => {
  // Always consume (read + clear) the sessionStorage fallback, even when the
  // server-provided `?redirect=` wins and the fallback value goes unused.
  // Otherwise a stale value survives this callback and can hijack a later,
  // unrelated login attempt that has no server-side target of its own (the
  // same hijack class fixed on the failure path in PR #69).
  const serverTarget = normalizeAuthRedirect(route.query.redirect)
  const pendingTarget = consumePendingAuthRedirect()
  return serverTarget ?? pendingTarget
}

// Builds the query for a failed-login redirect to /auth: the error always
// passes through unchanged (AuthChoiceView's banner depends on it), and the
// `redirect` key is only present when the URL carried a validated target --
// never an unvalidated one, and never an empty/undefined placeholder. Used
// by both failure paths below (the backend-reported failure and the
// unauthenticated-refreshUser() case) so a student who retries after either
// kind of failure still ends up where they originally wanted to go.
const buildAuthFailureQuery = (error: string): Record<string, string> => {
  const target = normalizeAuthRedirect(route.query.redirect)
  return target ? { error, redirect: target } : { error }
}

onMounted(async () => {
  // The backend's OAuth2 failure handler redirects here with `?error=...`
  // when it already knows the login failed, so there is no session to
  // fetch. Skip the pointless refreshUser() round trip in that case.
  if (typeof route.query.error === 'string') {
    // A stale pending redirect must not survive a failed login and hijack
    // the user's next, unrelated login attempt.
    clearPendingAuthRedirect()
    router.replace({ path: '/auth', query: buildAuthFailureQuery(route.query.error) })
    return
  }

  await authStore.refreshUser()

  if (authStore.isAuthenticated) {
    const redirectTarget = resolveRedirectTarget()
    const destination = resolvePostAuthRoute(authStore.currentUser, redirectTarget)
    router.replace(destination ?? DEFAULT_POST_AUTH_PATH)
  } else {
    clearPendingAuthRedirect()
    router.replace({ path: '/auth', query: buildAuthFailureQuery('login_failed') })
  }
})
</script>

<template>
  <div class="callback-page">
    <div class="callback-card">
      <span class="spinner" aria-hidden="true" />
      <p class="title">Finishing sign in…</p>
      <p class="hint">This only takes a moment. You will be redirected automatically.</p>
    </div>
  </div>
</template>

<style scoped>
.callback-page {
  min-height: 80vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(2rem, 5vw, 4rem) var(--page-padding-inline);
  background: var(--app-body-bg);
  color: var(--mv-text);
}

.callback-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: clamp(2rem, 4vw, 3rem);
  border-radius: var(--mv-radius);
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-hero-strong);
  box-shadow: var(--mv-shadow-elevated);
  max-width: 420px;
  text-align: center;
}

.spinner {
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  border: 3px solid var(--mv-surface-soft);
  border-top-color: var(--mv-gold);
  animation: spin 1s linear infinite;
}

.title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--mv-text);
}

.hint {
  margin: 0;
  color: var(--mv-text-faint);
  line-height: 1.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
