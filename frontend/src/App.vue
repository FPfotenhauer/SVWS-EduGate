<script setup lang="ts">
import { useAuthStore } from '@/auth/authStore'

const authStore = useAuthStore()
</script>

<template>
  <div class="app">
    <header class="app-header">
      <span class="app-title">SVWS-EduGate</span>
      <div v-if="authStore.isAuthenticated" class="app-user">
        <span>Angemeldet als {{ authStore.displayName }}</span>
        <button type="button" @click="authStore.logout()">Abmelden</button>
      </div>
    </header>
    <router-view />
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid #ddd;
}

.app-title {
  font-weight: 600;
}

.app-user {
  display: flex;
  align-items: center;
  gap: 1rem;
}
</style>
