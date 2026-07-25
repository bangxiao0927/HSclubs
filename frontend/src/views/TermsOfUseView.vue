<script setup lang="ts">
import { schoolTemplate } from '../config/schoolTemplate'

const brandName = schoolTemplate.brandName
</script>

<template>
  <div class="legal-page page-shell">
    <header class="legal-hero">
      <p class="section-label">Legal</p>
      <h1>Terms of Use</h1>
      <p>Last updated: July 2026</p>
    </header>

    <section class="legal-content">
      <h2>1. Acceptance of Terms</h2>
      <p>
        By accessing or using {{ brandName }} ("the Platform"), you agree to be bound by these Terms
        of Use. If you do not agree, do not use the Platform.
      </p>

      <h2>2. Eligibility</h2>
      <p>
        The Platform is intended for high school students, staff, and administrators. You must use a
        valid school-issued or school-affiliated Google account to sign in.
      </p>

      <h2>3. User Conduct</h2>
      <p>Users agree to:</p>
      <ul>
        <li>Provide accurate profile information.</li>
        <li>Not impersonate others or misrepresent club affiliations.</li>
        <li>Not post harmful, harassing, or inappropriate content.</li>
        <li>Respect the privacy of other users.</li>
      </ul>

      <h2>4. Platform Role</h2>
      <p>
        {{ brandName }} provides a directory and management tool for school clubs. Individual
        schools and club leaders are responsible for the content and accuracy of their club
        listings.
      </p>

      <h2>5. Account Termination</h2>
      <p>
        Platform owners reserve the right to suspend or terminate accounts that violate these terms
        or disrupt the community.
      </p>

      <h2>6. Changes to Terms</h2>
      <p>
        These terms may be updated periodically. Continued use of the Platform after changes
        constitutes acceptance of the updated terms.
      </p>

      <h2>7. Contact</h2>
      <p>
        Questions about these terms? Contact the platform administrator through your school's club
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
