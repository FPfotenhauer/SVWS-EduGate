<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <main>
    <h1>Einstellungen</h1>
    <p class="hinweis">Betreiber-Einstellungen für SVWS-EduGate.</p>

    <div class="kacheln">
      <RouterLink :to="{ name: 'schuldatei' }" class="kachel">
        <h2>Schuldatei</h2>
        <p>Amtliche Landes-Schuldatei als Referenzkatalog: Schulen und Schulträger einsehen, suchen und abgleichen.</p>
      </RouterLink>

      <RouterLink :to="{ name: 'schema-umgebungen' }" class="kachel">
        <h2>Umgebungen verwalten</h2>
        <p>Namenskonvention für Schuldatenbanken: Produktiv, Test, Schulung und weitere Umgebungen pflegen.</p>
      </RouterLink>

      <article class="kachel kachel-deaktiviert">
        <h2>Benutzer verwalten</h2>
        <p>Dienstleister-Admins und deren Rechte verwalten.</p>
        <span class="badge">In Vorbereitung</span>
      </article>
    </div>
  </main>
</template>

<style scoped>
.hinweis {
  color: var(--ink-soft);
  max-width: 40rem;
}

.kacheln {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 1rem;
  margin-top: 1.5rem;
}

.kachel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 1.25rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  text-decoration: none;
  color: var(--ink);
}

a.kachel:hover {
  border-color: var(--accent);
}

.kachel h2 {
  margin: 0;
  font-size: 1.05rem;
}

.kachel p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 0.9rem;
}

.kachel-deaktiviert {
  opacity: 0.7;
  cursor: not-allowed;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  background: var(--surface-strong);
  color: var(--ink-soft);
  font-size: 0.78rem;
  font-weight: 600;
}
</style>
