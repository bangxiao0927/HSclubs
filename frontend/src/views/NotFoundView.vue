<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="not-found">
    <div class="nf-card">
      <p class="nf-code">404</p>
      <h1>Page not found</h1>
      <p>The page you're looking for doesn't exist or has been moved.</p>
      <div class="nf-actions">
        <RouterLink to="/schools" class="btn primary">Browse schools</RouterLink>
        <RouterLink to="/" class="btn ghost">Go home</RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.nf-card {
  text-align: center;
  max-width: 420px;
}

.nf-code {
  font-size: 5rem;
  font-weight: 800;
  margin: 0;
  color: var(--mv-gold);
  line-height: 1;
}

.nf-card h1 { margin: 0.5rem 0; }
.nf-card p { color: var(--mv-text-muted); margin: 0 0 1.5rem; }

.nf-actions { display: flex; gap: 0.75rem; justify-content: center; }

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  text-decoration: none;
}
.btn.primary { background: var(--mv-primary-bg); color: var(--mv-primary-text); }
.btn.ghost { border: 1px solid var(--mv-ghost-border); color: var(--mv-ghost-text); }

@media (max-width: 480px) {
  .nf-code {
    font-size: 3.5rem;
  }

  .nf-actions {
    flex-direction: column;
    width: 100%;
  }

  .nf-actions .btn {
    width: 100%;
    text-align: center;
  }
}
</style>
