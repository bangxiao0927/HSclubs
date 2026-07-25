<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'
import {
  consumePendingAuthRedirect,
  DEFAULT_POST_AUTH_PATH,
  normalizeAuthRedirect,
  resolvePostAuthRoute,
} from '../utils/authRedirect'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const resolveRedirectTarget = () => {
  return (
    normalizeAuthRedirect(route.query.redirect) ??
    consumePendingAuthRedirect() ??
    DEFAULT_POST_AUTH_PATH
  )
}

onMounted(async () => {
  await authStore.refreshUser()

  if (authStore.isAuthenticated) {
    const redirectTarget = resolveRedirectTarget()
    const destination = resolvePostAuthRoute(authStore.currentUser, redirectTarget)
    router.replace(destination ?? redirectTarget)
  } else {
    router.replace({ path: '/auth', query: { error: 'login_failed' } })
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
