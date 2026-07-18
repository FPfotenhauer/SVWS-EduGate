<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import type { SvwsInstanz } from '@/types/svwsInstanz'

const store = useSvwsInstanzStore()
const suchbegriff = ref('')
const zuDeaktivieren = ref<SvwsInstanz | null>(null)

const statusLabel: Record<string, string> = { OK: 'OK', DEGRADED: 'Beeinträchtigt', UNREACHABLE: 'Nicht erreichbar' }

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
      <h1>SVWS-Instanzen</h1>
      <RouterLink :to="{ name: 'svws-instanz-neu' }" class="button-primary">Neue Instanz anlegen</RouterLink>
    </header>

    <form class="search" @submit.prevent="suchen">
      <label for="suche">Suche nach Name oder Base-URL</label>
      <input id="suche" v-model="suchbegriff" type="search" placeholder="z. B. svws.example.org" />
      <button type="submit">Suchen</button>
    </form>

    <p v-if="store.errorMessage" role="alert" class="fehler">{{ store.errorMessage }}</p>
    <p v-else-if="store.loading">Lädt …</p>

    <div v-else class="table-wrap">
      <table>
        <caption class="sr-only">
          Liste der SVWS-Instanzen
        </caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Base-URL</th>
            <th scope="col">Status</th>
            <th scope="col">Aktiv/Inaktiv</th>
            <th scope="col">Aktionen</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="instanz in store.items" :key="instanz.id">
            <td>{{ instanz.name }}</td>
            <td>{{ instanz.baseUrl }}</td>
            <td>
              <span :class="['status', `status-${instanz.status.toLowerCase()}`]">
                {{ statusLabel[instanz.status] ?? instanz.status }}
              </span>
            </td>
            <td>
              <span :class="['status', instanz.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                {{ instanz.aktiv ? 'Aktiv' : 'Deaktiviert' }}
              </span>
            </td>
            <td class="aktionen">
              <RouterLink :to="{ name: 'svws-instanz-bearbeiten', params: { id: instanz.id } }">
                Bearbeiten
              </RouterLink>
              <button v-if="instanz.aktiv" type="button" class="danger" @click="zuDeaktivieren = instanz">
                Deaktivieren
              </button>
            </td>
          </tr>
          <tr v-if="store.items.length === 0">
            <td colspan="5">Keine SVWS-Instanzen gefunden.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <nav class="pagination" aria-label="Seitennavigation">
      <button type="button" :disabled="store.page === 0" @click="vorherigeSeite">Zurück</button>
      <span>Seite {{ store.page + 1 }} von {{ Math.max(1, Math.ceil(store.totalElements / store.size)) }}</span>
      <button type="button" :disabled="(store.page + 1) * store.size >= store.totalElements" @click="naechsteSeite">
        Weiter
      </button>
    </nav>

    <ConfirmDialog
      :open="zuDeaktivieren !== null"
      titel="SVWS-Instanz deaktivieren"
      :nachricht="`Soll '${zuDeaktivieren?.name}' wirklich deaktiviert werden?`"
      @confirm="bestaetigenDeaktivieren"
      @cancel="zuDeaktivieren = null"
    />
  </main>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 1rem 0;
}

.search input {
  flex: 1 1 12rem;
  min-width: 0;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 40rem;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem;
  border-bottom: 1px solid var(--line);
}

.aktionen {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.status {
  font-weight: 600;
}

.status-aktiv,
.status-ok {
  color: var(--accent);
}

.status-inaktiv {
  color: var(--ink-soft);
}

.status-degraded {
  color: #b45309;
}

.status-unreachable {
  color: var(--error);
}

.pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  margin-top: 1rem;
}

.fehler {
  color: var(--error);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

@media (max-width: 640px) {
  .toolbar {
    justify-content: center;
    text-align: center;
  }
}
</style>
