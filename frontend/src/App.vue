<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/auth/authStore'
import { useTheme, type ThemePreference } from '@/composables/useTheme'

const authStore = useAuthStore()
const { preference, setTheme } = useTheme()

const themeOrder: ThemePreference[] = ['system', 'light', 'dark']
const themeLabel: Record<ThemePreference, string> = { system: 'System', light: 'Hell', dark: 'Dunkel' }

function cycleTheme(): void {
  const next = themeOrder[(themeOrder.indexOf(preference.value) + 1) % themeOrder.length]
  setTheme(next)
}
</script>

<template>
  <div class="app">
    <header class="app-header">
      <span class="app-title">SVWS-EduGate</span>
      <nav v-if="authStore.isAuthenticated" class="app-nav" aria-label="Hauptnavigation">
        <RouterLink :to="{ name: 'schultraeger-liste' }">Schulträger</RouterLink>
        <RouterLink :to="{ name: 'svws-instanz-liste' }">SVWS-Instanzen</RouterLink>
      </nav>
      <div class="app-header-actions">
        <button
          type="button"
          class="theme-btn"
          :title="`Theme: ${themeLabel[preference]} (klicken zum Wechseln)`"
          :aria-label="`Theme: ${themeLabel[preference]} (klicken zum Wechseln)`"
          @click="cycleTheme"
        >
          {{ themeLabel[preference] }}
        </button>
        <div v-if="authStore.isAuthenticated" class="app-user">
          <span>Angemeldet als {{ authStore.displayName }}</span>
          <button type="button" @click="authStore.logout()">Abmelden</button>
        </div>
      </div>
    </header>
    <router-view />
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}

.app-title {
  font-weight: 700;
  color: var(--accent);
}

.app-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1.25rem;
}

.app-nav a {
  color: var(--ink-soft);
  text-decoration: none;
  font-weight: 600;
}

.app-nav a:hover,
.app-nav a.router-link-active {
  color: var(--accent);
}

.app-header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.app-user {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

@media (max-width: 640px) {
  .app-header {
    justify-content: center;
    text-align: center;
  }

  .app-nav,
  .app-header-actions,
  .app-user {
    justify-content: center;
    width: 100%;
  }
}
</style>
