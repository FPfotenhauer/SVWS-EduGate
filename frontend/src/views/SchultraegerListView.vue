<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useSchultraegerStore } from '@/stores/schultraegerStore'

const store = useSchultraegerStore()
const suchbegriff = ref('')

// Auswahl je Schulträger-ID, seitenübergreifend - Vorbereitung für spätere Massenverwaltung
// (analog zur SVWS-Instanzen-Liste). Aktuell noch ohne eigene Aktion, nur die Auswahl-UI.
const ausgewaehlt = ref<Record<string, boolean>>({})

const alleAufSeiteAusgewaehlt = computed(
  () => store.items.length > 0 && store.items.every((schultraeger) => ausgewaehlt.value[schultraeger.id]),
)
const teilweiseAusgewaehlt = computed(
  () => !alleAufSeiteAusgewaehlt.value && store.items.some((schultraeger) => ausgewaehlt.value[schultraeger.id]),
)

function alleAufSeiteUmschalten(): void {
  const neuerWert = !alleAufSeiteAusgewaehlt.value
  for (const schultraeger of store.items) {
    ausgewaehlt.value[schultraeger.id] = neuerWert
  }
}

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

    <div v-else class="table-wrap">
      <table>
        <caption class="sr-only">
          Liste der Schulträger
        </caption>
        <thead>
          <tr>
            <th scope="col" class="checkbox-zelle">
              <input
                type="checkbox"
                aria-label="Alle auf dieser Seite auswählen"
                :checked="alleAufSeiteAusgewaehlt"
                :indeterminate="teilweiseAusgewaehlt"
                @change="alleAufSeiteUmschalten"
              />
            </th>
            <th scope="col">ID</th>
            <th scope="col">Name</th>
            <th scope="col">Trägernummer</th>
            <th scope="col">Status</th>
            <th scope="col">Aktionen</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="schultraeger in store.items" :key="schultraeger.id">
            <td class="checkbox-zelle">
              <input
                v-model="ausgewaehlt[schultraeger.id]"
                type="checkbox"
                :aria-label="`'${schultraeger.name}' auswählen`"
              />
            </td>
            <td class="id-zelle" :title="schultraeger.id">{{ schultraeger.id }}</td>
            <td>{{ schultraeger.name }}</td>
            <td>{{ schultraeger.traegernummer }}</td>
            <td>
              <span :class="['status', schultraeger.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                {{ schultraeger.aktiv ? 'Aktiv' : 'Deaktiviert' }}
              </span>
            </td>
            <td>
              <RouterLink
                :to="{ name: 'schultraeger-bearbeiten', params: { id: schultraeger.id } }"
                class="button-secondary"
              >
                Bearbeiten
              </RouterLink>
            </td>
          </tr>
          <tr v-if="store.items.length === 0">
            <td colspan="6">Keine Schulträger gefunden.</td>
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
  border: 1px solid var(--line);
  border-radius: 10px;
  scrollbar-width: thin;
  scrollbar-color: var(--line) transparent;
}

.table-wrap::-webkit-scrollbar {
  height: 8px;
}

.table-wrap::-webkit-scrollbar-track {
  background: transparent;
}

.table-wrap::-webkit-scrollbar-thumb {
  background-color: var(--line);
  border-radius: 999px;
}

.table-wrap::-webkit-scrollbar-thumb:hover {
  background-color: var(--accent);
}

table {
  width: 100%;
  min-width: 40rem;
  border-collapse: collapse;
  font-size: 0.9rem;
}

th,
td {
  text-align: left;
  padding: 0.35rem 0.6rem;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
  line-height: 1.3;
}

thead th {
  padding-top: 0.5rem;
  padding-bottom: 0.5rem;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--ink-soft);
  background: var(--surface-strong);
}

tbody tr:hover {
  background: var(--surface-strong);
}

.checkbox-zelle {
  width: 1%;
  padding-right: 0;
  text-align: center;
}

.id-zelle {
  max-width: 9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.8em;
  color: var(--ink-soft);
}

.button-secondary {
  display: inline-block;
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  text-decoration: none;
  cursor: pointer;
}

.button-secondary:hover {
  border-color: var(--accent);
}

.status {
  font-weight: 600;
  white-space: nowrap;
}

.status-aktiv {
  color: var(--accent);
}

.status-inaktiv {
  color: var(--ink-soft);
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
