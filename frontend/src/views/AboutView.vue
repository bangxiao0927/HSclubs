<template>
  <section class="about-page">
    <div class="about-card">
      <p class="section-label">Campus map</p>
      <h1>Connected programming across Mountain View</h1>
      <p>
        The Activities office is layering the black + gold system onto feeder schools and partner programs. Shared assets keep clubs coordinated while letting every
        campus highlight their own wins.
      </p>
      <ul>
        <li><strong>Graham Middle ·</strong> Broadcasting club using MVHS livestream templates</li>
        <li><strong>Crittenden Middle ·</strong> Robotics scrimmages import Spartan rubrics</li>
        <li><strong>City of Mountain View ·</strong> Service projects sync with Trail Stewards</li>
      </ul>
      <button type="button">Join the pilot</button>
    </div>
  </section>
</template>

<style scoped>
.about-page {
  display: flex;
  justify-content: center;
  padding: 2rem 0 3rem;
}

.about-card {
  width: min(760px, 100%);
  background: radial-gradient(circle at 20% 15%, rgba(250, 204, 21, 0.28), rgba(6, 6, 6, 0.95));
  border: 1px solid rgba(250, 204, 21, 0.25);
  border-radius: 32px;
  padding: clamp(1.75rem, 4vw, 3rem);
  box-shadow: 0 25px 65px rgba(0, 0, 0, 0.55);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.about-card h1 {
  font-size: 2rem;
  margin: 0;
}

.about-card p {
  color: var(--mv-text-muted);
}

.about-card ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  color: var(--mv-text);
}

.about-card button {
  align-self: flex-start;
  border-radius: 16px;
  border: 1px solid rgba(250, 204, 21, 0.35);
  background: linear-gradient(120deg, #fde047, #facc15, #f59e0b);
  color: #050505;
  padding: 0.85rem 1.7rem;
  font-weight: 600;
  cursor: pointer;
}

.about-card strong {
  color: var(--mv-gold);
}
</style>
