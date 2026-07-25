<script setup lang="ts">
import { schoolTemplate } from '../config/schoolTemplate'

const brandName = schoolTemplate.brandName
</script>

<template>
  <div class="legal-page page-shell">
    <header class="legal-hero">
      <p class="section-label">Legal</p>
      <h1>Privacy Policy</h1>
      <p>Last updated: July 2026</p>
    </header>

    <section class="legal-content">
      <h2>1. Information We Collect</h2>
      <p>
        When you sign in with Google OAuth, we receive your name, email address, and profile picture
        from Google. We also collect the graduation year and club memberships you provide.
      </p>

      <h2>2. How We Use Information</h2>
      <p>Your information is used to:</p>
      <ul>
        <li>Display your profile to other users within your school community.</li>
        <li>Process club membership applications and manage club rosters.</li>
        <li>Enable club presidents and school administrators to manage their clubs.</li>
      </ul>

      <h2>3. Data Sharing</h2>
      <p>
        We do not sell or share your personal information with third parties. Your data stays within
        the {{ brandName }} platform and your school's instance.
      </p>

      <h2>4. Data Retention</h2>
      <p>
        Your account data is retained as long as your account is active. You may request account
        deletion by contacting your school administrator or the platform owner.
      </p>

      <h2>5. Security</h2>
      <p>
        We use session-based authentication via Google OAuth 2.0. All API access requires
        authentication for protected operations. However, no online service is 100% secure.
      </p>

      <h2>6. Children's Privacy</h2>
      <p>
        The Platform is designed for high school students. We comply with applicable student data
        privacy laws. Parents or guardians with concerns may contact the platform administrator.
      </p>

      <h2>7. Contact</h2>
      <p>
        For privacy-related questions, contact the platform administrator through your school's club
        directory page.
      </p>
    </section>
  </div>
</template>

<style scoped>
.legal-page {
  padding-block: clamp(2rem, 5vw, 4rem);
}

.legal-hero {
  margin-bottom: 2.5rem;
}

.legal-hero h1 {
  font-size: clamp(1.8rem, 3vw, 2.6rem);
  font-weight: 700;
  color: var(--mv-text);
  margin: 0.5rem 0 0.25rem;
}

.legal-hero p {
  color: var(--mv-text-muted);
  margin: 0;
}

.legal-content {
  max-width: 780px;
}

.legal-content h2 {
  font-size: 1.2rem;
  color: var(--mv-text);
  margin: 2rem 0 0.5rem;
}

.legal-content h2:first-child {
  margin-top: 0;
}

.legal-content p,
.legal-content li {
  color: var(--mv-text-muted);
  line-height: 1.7;
}

.legal-content ul {
  padding-left: 1.5rem;
  margin: 0.5rem 0;
}
</style>
