<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/authStore'

const router = useRouter()
const authStore = useAuthStore()
const fehler = ref<string | null>(null)

onMounted(async () => {
  try {
    await authStore.completeLogin()
    await router.replace({ name: 'schultraeger-liste' })
  } catch (error) {
    fehler.value = error instanceof Error ? error.message : 'Anmeldung fehlgeschlagen.'
  }
})
</script>

<template>
  <main class="callback">
    <p v-if="!fehler">Anmeldung wird abgeschlossen …</p>
    <p v-else role="alert" class="fehler">{{ fehler }}</p>
  </main>
</template>

<style scoped>
.callback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.fehler {
  color: var(--error);
}
</style>
