<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { schoolTemplate } from '../config/schoolTemplate'

const brandName = schoolTemplate.brandName

const clearCache = () => {
  try {
    localStorage.clear()
  } catch {
    /* ignore */
  }
  window.location.reload()
}
</script>

<template>
  <div class="settings-page page-shell">
    <header class="settings-hero">
      <p class="section-label">Settings</p>
      <h1>Preferences</h1>
      <p>Customize your {{ brandName }} experience.</p>
    </header>

    <section class="settings-group">
      <h2>Appearance</h2>
      <div class="setting-row">
        <div>
          <strong>Theme</strong>
          <p>Toggle between light and dark mode using the button in the header.</p>
        </div>
      </div>
    </section>

    <section class="settings-group">
      <h2>Account</h2>
      <RouterLink to="/profile" class="setting-row link">
        <div>
          <strong>Profile</strong>
          <p>Manage your personal information, clubs, and graduation year.</p>
        </div>
        <span>→</span>
      </RouterLink>
    </section>

    <section class="settings-group">
      <h2>Data</h2>
      <div class="setting-row">
        <div>
          <strong>Clear local cache</strong>
          <p>Resets theme preference and cached club data. Requires page reload.</p>
        </div>
        <button type="button" class="btn ghost small" @click="clearCache">Clear</button>
      </div>
    </section>

    <section class="settings-group">
      <h2>About</h2>
      <div class="setting-row">
        <div>
          <strong>{{ brandName }}</strong>
          <p>Single-school club directory platform. Built with Vue 3 + Spring Boot.</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: clamp(1.5rem, 4vw, 2.5rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
  max-width: 640px;
}

.settings-hero {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.settings-hero h1 {
  margin: 0;
}

.settings-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.settings-group h2 {
  margin: 0;
  font-size: 1rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--mv-text-dim);
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: 18px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  text-decoration: none;
  color: inherit;
}

.setting-row > div {
  min-width: 0;
  overflow-wrap: anywhere;
}

.setting-row.link:hover {
  background: var(--mv-surface-accent);
}

.setting-row strong {
  font-size: 0.95rem;
}
.setting-row p {
  margin: 0.25rem 0 0;
  color: var(--mv-text-muted);
  font-size: 0.85rem;
}

.btn {
  padding: 0.4rem 1rem;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
  border: 1px solid var(--mv-ghost-border);
  background: transparent;
  color: var(--mv-ghost-text);
  flex-shrink: 0;
}
.btn:hover {
  background: var(--mv-surface-accent);
}

@media (max-width: 480px) {
  .setting-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .setting-row .btn {
    width: 100%;
    text-align: center;
  }

  .settings-hero h1 {
    font-size: 1.5rem;
  }
}
</style>
