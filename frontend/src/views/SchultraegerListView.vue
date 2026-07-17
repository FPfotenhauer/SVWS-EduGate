<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import type { Schultraeger } from '@/types/schultraeger'

const store = useSchultraegerStore()
const suchbegriff = ref('')
const zuDeaktivieren = ref<Schultraeger | null>(null)

onMounted(() => store.fetchList())

function suchen(): void {
  store.fetchList({ page: 0, q: suchbegriff.value })
}

function naechsteSeite(): void {
  if ((store.page + 1) * store.size < store.totalElements) {
    store.fetchList({ page: store.page + 1 })
  }
}

function vorherigeSeite(): void {
  if (store.page > 0) {
    store.fetchList({ page: store.page - 1 })
  }
}

async function bestaetigenDeaktivieren(): Promise<void> {
  if (!zuDeaktivieren.value) return
  await store.deactivate(zuDeaktivieren.value.id)
  zuDeaktivieren.value = null
}
</script>

<template>
  <main>
    <header class="toolbar">
      <h1>Schulträger</h1>
      <RouterLink :to="{ name: 'schultraeger-neu' }" class="button-primary">Neuen Schulträger anlegen</RouterLink>
    </header>

    <form class="search" @submit.prevent="suchen">
      <label for="suche">Suche nach Name oder Trägernummer</label>
      <input id="suche" v-model="suchbegriff" type="search" placeholder="z. B. Musterstadt" />
      <button type="submit">Suchen</button>
    </form>

    <p v-if="store.errorMessage" role="alert" class="fehler">{{ store.errorMessage }}</p>
    <p v-else-if="store.loading">Lädt …</p>

    <table v-else>
      <caption class="sr-only">
        Liste der Schulträger
      </caption>
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col">Trägernummer</th>
          <th scope="col">Status</th>
          <th scope="col">Aktionen</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="schultraeger in store.items" :key="schultraeger.id">
          <td>{{ schultraeger.name }}</td>
          <td>{{ schultraeger.traegernummer }}</td>
          <td>
            <span :class="['status', schultraeger.aktiv ? 'status-aktiv' : 'status-inaktiv']">
              {{ schultraeger.aktiv ? 'Aktiv' : 'Deaktiviert' }}
            </span>
          </td>
          <td class="aktionen">
            <RouterLink :to="{ name: 'schultraeger-bearbeiten', params: { id: schultraeger.id } }">
              Bearbeiten
            </RouterLink>
            <button v-if="schultraeger.aktiv" type="button" @click="zuDeaktivieren = schultraeger">Deaktivieren</button>
          </td>
        </tr>
        <tr v-if="store.items.length === 0">
          <td colspan="4">Keine Schulträger gefunden.</td>
        </tr>
      </tbody>
    </table>

    <nav class="pagination" aria-label="Seitennavigation">
      <button type="button" :disabled="store.page === 0" @click="vorherigeSeite">Zurück</button>
      <span>Seite {{ store.page + 1 }} von {{ Math.max(1, Math.ceil(store.totalElements / store.size)) }}</span>
      <button type="button" :disabled="(store.page + 1) * store.size >= store.totalElements" @click="naechsteSeite">
        Weiter
      </button>
    </nav>

    <ConfirmDialog
      :open="zuDeaktivieren !== null"
      titel="Schulträger deaktivieren"
      :nachricht="`Soll '${zuDeaktivieren?.name}' wirklich deaktiviert werden?`"
      @confirm="bestaetigenDeaktivieren"
      @cancel="zuDeaktivieren = null"
    />
  </main>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 1rem 0;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem;
  border-bottom: 1px solid #ddd;
}

.aktionen {
  display: flex;
  gap: 0.75rem;
}

.status-aktiv {
  color: #1a7f37;
}

.status-inaktiv {
  color: #6e7781;
}

.pagination {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1rem;
}

.fehler {
  color: #b3261e;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
