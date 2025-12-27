<script setup lang="ts">
import { RouterLink } from 'vue-router'

const quickLinks = [
  { label: 'Complete profile', description: 'Add grade, contact info, and interests.' },
  { label: 'My clubs', description: 'Review clubs you follow or applied to.' },
  { label: 'Inbox', description: 'Stay on top of announcements and approvals.' },
]

const reminders = [
  'Create an account to save favorite clubs.',
  'Sign in to track application status and event check-ins.',
  'Keeping details current helps advisors reach you quickly.',
]
</script>

<template>
  <div class="profile page-shell">
    <section class="profile-hero">
      <div class="hero-copy">
        <p class="section-label">Personal hub</p>
        <h1>Profile hub</h1>
        <p>
          Keep every club interaction in one place—favorites, applications, reminders, and more. Register or sign in to unlock the personalized experience.
        </p>
        <div class="cta-group">
          <RouterLink to="/profile?mode=register" class="btn primary">Create account</RouterLink>
          <RouterLink to="/profile?mode=login" class="btn ghost">Already a member? Sign in</RouterLink>
        </div>
      </div>
      <div class="hero-card">
        <div class="avatar">👤</div>
        <p class="hero-card-label">Welcome, new member</p>
        <p class="hero-card-desc">Sign in to personalize recommendations.</p>
      </div>
    </section>

    <section class="profile-grid">
      <article class="card">
        <h2>Shortcuts</h2>
        <p class="card-subtitle">These actions unlock once you create or access your account.</p>
        <ul class="quick-links">
          <li v-for="link in quickLinks" :key="link.label">
            <h3>{{ link.label }}</h3>
            <p>{{ link.description }}</p>
          </li>
        </ul>
      </article>

      <article class="card reminders">
        <h2>Reminders</h2>
        <ul>
          <li v-for="message in reminders" :key="message">{{ message }}</li>
        </ul>
      </article>
    </section>
  </div>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: clamp(2rem, 4vw, 3rem);
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.profile-hero {
  display: flex;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 36px;
  border: 1px solid rgba(250, 204, 21, 0.2);
  background: linear-gradient(120deg, rgba(250, 204, 21, 0.18), rgba(5, 5, 5, 0.95));
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
}

.hero-copy {
  max-width: 560px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3rem);
}

.cta-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.btn {
  padding: 0.85rem 1.5rem;
  border-radius: 999px;
  font-weight: 600;
  text-decoration: none;
  transition: transform 0.2s, box-shadow 0.2s;
}

.btn.primary {
  background: #fde047;
  color: #111;
  box-shadow: 0 10px 30px rgba(253, 224, 71, 0.35);
}

.btn.ghost {
  border: 1px solid rgba(254, 252, 232, 0.4);
  color: #fefce8;
}

.btn:hover {
  transform: translateY(-1px);
}

.hero-card {
  min-width: 220px;
  flex: 1;
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.25);
  background: rgba(0, 0, 0, 0.55);
  padding: 2rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  margin: 0 auto;
  background: rgba(253, 224, 71, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
}

.hero-card-label {
  font-weight: 600;
}

.hero-card-desc {
  margin: 0;
  color: rgba(254, 252, 232, 0.75);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.5rem;
}

.card {
  border-radius: 28px;
  border: 1px solid rgba(250, 204, 21, 0.18);
  background: rgba(7, 7, 7, 0.9);
  padding: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: 0 25px 40px rgba(0, 0, 0, 0.35);
}

.card h2 {
  margin: 0;
}

.card-subtitle {
  margin: 0;
  color: rgba(254, 252, 232, 0.7);
}

.quick-links {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.quick-links li {
  padding: 1rem;
  border-radius: 20px;
  border: 1px solid rgba(254, 252, 232, 0.08);
  background: rgba(254, 252, 232, 0.03);
}

.quick-links h3 {
  margin: 0 0 0.35rem;
}

.quick-links p {
  margin: 0;
  color: rgba(254, 252, 232, 0.7);
}

.reminders ul {
  list-style: disc;
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.reminders li {
  color: rgba(254, 252, 232, 0.75);
}

@media (max-width: 640px) {
  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-card {
    width: 100%;
  }
}
</style>
