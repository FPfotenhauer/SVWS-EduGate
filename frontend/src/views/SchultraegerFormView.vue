<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { ApiError } from '@/types/problem'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useSchultraegerStore()

const name = ref('')
const traegernummer = ref('')
const speichern = ref(false)
const fehler = ref<string | null>(null)

const bearbeitenModus = !!props.id

onMounted(async () => {
  if (props.id) {
    const bestehender = await store.get(props.id)
    name.value = bestehender.name
    traegernummer.value = bestehender.traegernummer
  }
})

async function absenden(): Promise<void> {
  fehler.value = null
  speichern.value = true
  try {
    if (props.id) {
      await store.update(props.id, { name: name.value, traegernummer: traegernummer.value })
    } else {
      await store.create({ name: name.value, traegernummer: traegernummer.value })
    }
    await router.push({ name: 'schultraeger-liste' })
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}
</script>

<template>
  <main>
    <h1>{{ bearbeitenModus ? 'Schulträger bearbeiten' : 'Neuen Schulträger anlegen' }}</h1>

    <form @submit.prevent="absenden">
      <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

      <div class="feld">
        <label for="name">Name</label>
        <input id="name" v-model="name" type="text" required />
      </div>

      <div class="feld">
        <label for="traegernummer">Trägernummer</label>
        <input id="traegernummer" v-model="traegernummer" type="text" required />
      </div>

      <div class="aktionen">
        <button type="submit" :disabled="speichern">Speichern</button>
        <RouterLink :to="{ name: 'schultraeger-liste' }">Abbrechen</RouterLink>
      </div>
    </form>
  </main>
</template>

<style scoped>
.feld {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 1rem;
  max-width: 24rem;
}

.aktionen {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.fehler {
  color: #b3261e;
}
</style>
