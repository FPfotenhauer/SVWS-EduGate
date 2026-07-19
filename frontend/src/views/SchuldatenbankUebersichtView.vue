<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Modal from '@/components/Modal.vue'
import { useSchemaOverviewStore } from '@/stores/schemaOverviewStore'
import { useSchemaUmgebungStore } from '@/stores/schemaUmgebungStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { SchemaStatus } from '@/types/schema'

// Fachliche Sicht "Schulträger/Schule -> Schuldatenbanken -> SVWS-Instanz" (ADR-013): primärer
// Einstieg für die Schemaverwaltung, mandantenübergreifend gefiltert/gruppiert statt unter
// "Schulträger bearbeiten" versteckt.
const route = useRoute()
const router = useRouter()
const store = useSchemaOverviewStore()
const schultraegerStore = useSchultraegerStore()
const svwsInstanzStore = useSvwsInstanzStore()
const schuleStore = useSchuleStore()
const schemaUmgebungStore = useSchemaUmgebungStore()

// Kontext aus Querverweisen (ADR-013: "Navigation zwischen den Sichten muss den Kontext
// erhalten"), z. B. von der Schulträger- oder der SVWS-Instanz-Seite kommend.
const anfangsSchultraegerId = typeof route.query.schultraegerId === 'string' ? route.query.schultraegerId : ''
const anfangsInstanzId = typeof route.query.instanzId === 'string' ? route.query.instanzId : ''

const suchbegriff = ref('')
const schultraegerFilter = ref(anfangsSchultraegerId)
const instanzFilter = ref(anfangsInstanzId)
const umgebungFilter = ref('')
const statusFilter = ref<SchemaStatus | ''>('')

const statusOptionen: { value: SchemaStatus; label: string }[] = [
  { value: 'GEPLANT', label: 'Geplant' },
  { value: 'VORHANDEN', label: 'Vorhanden' },
  { value: 'AKTIV', label: 'Aktiv' },
  { value: 'DEAKTIVIERT', label: 'Deaktiviert' },
  { value: 'MIGRATION_ERFORDERLICH', label: 'Migration erforderlich' },
  { value: 'FEHLER', label: 'Fehler' },
  { value: 'ARCHIVIERT', label: 'Archiviert' },
]

onMounted(async () => {
  // Für die Filter-Dropdowns wird eine größere Seite geladen als der Listen-Standard (25) - bei
  // sehr vielen Schulträgern/Instanzen bräuchte das eine eigene Such-Combobox statt eines
  // einfachen <select>; für diesen Navigations-Durchstich reicht eine großzügigere Seitengröße.
  schultraegerStore.size = 100
  svwsInstanzStore.size = 100
  await Promise.all([
    store.fetchList({ page: 0, schultraegerId: anfangsSchultraegerId, instanzId: anfangsInstanzId }),
    schultraegerStore.fetchList({ page: 0 }),
    svwsInstanzStore.fetchList({ page: 0 }),
    schemaUmgebungStore.fetchList(),
  ])
})

function suchen(): void {
  store.fetchList({
    page: 0,
    q: suchbegriff.value,
    schultraegerId: schultraegerFilter.value,
    instanzId: instanzFilter.value,
    umgebung: umgebungFilter.value,
    status: statusFilter.value,
  })
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

// --- Neue Schule anlegen: bewusst nur der erste Schritt (Schulträger + Stammdaten). Der
// vollständige geführte Workflow in einem einzigen Formular (Zielinstanz, Namenskonvention,
// Umgebung, Credential-/API-Zugriff, ADR-013) ist für diesen Durchstich ausdrücklich nicht Teil
// des Auftrags - stattdessen wird nach dem Anlegen direkt auf die Schulseite weitergeleitet und
// dort automatisch der (dort bereits vorhandene) Dialog "Schuldatenbank anlegen" geöffnet, in dem
// die SVWS-Instanz ausgewählt wird. So landet niemand ohne Möglichkeit, den Zielserver zu wählen.
const aktiveSchultraeger = computed(() => schultraegerStore.items.filter((schultraeger) => schultraeger.aktiv))

const neueSchuleModalOffen = ref(false)
const neueSchuleSchultraegerId = ref('')
const neueSchuleSchulnummer = ref('')
const neueSchuleName = ref('')
const neueSchuleSpeichern = ref(false)
const neueSchuleFehler = ref<string | null>(null)

function neueSchuleOeffnen(): void {
  neueSchuleSchultraegerId.value = aktiveSchultraeger.value[0]?.id ?? ''
  neueSchuleSchulnummer.value = ''
  neueSchuleName.value = ''
  neueSchuleFehler.value = null
  neueSchuleModalOffen.value = true
}

function neueSchuleSchliessen(): void {
  neueSchuleModalOffen.value = false
}

async function neueSchuleAbsenden(): Promise<void> {
  neueSchuleFehler.value = null
  neueSchuleSpeichern.value = true
  try {
    const angelegteSchule = await schuleStore.create(neueSchuleSchultraegerId.value, {
      schulnummer: neueSchuleSchulnummer.value,
      name: neueSchuleName.value,
    })
    neueSchuleModalOffen.value = false
    await router.push({
      name: 'schule-detail',
      params: { schultraegerId: neueSchuleSchultraegerId.value, schuleId: angelegteSchule.id },
      query: { schuldatenbankAnlegen: '1' },
    })
  } catch (error) {
    neueSchuleFehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    neueSchuleSpeichern.value = false
  }
}
</script>

<template>
  <main>
    <header class="toolbar">
      <h1>Schuldatenbanken</h1>
      <button type="button" class="button-primary" @click="neueSchuleOeffnen">Neue Schule anlegen</button>
    </header>

    <form class="search" @submit.prevent="suchen">
      <label for="suche">Suche</label>
      <input id="suche" v-model="suchbegriff" type="search" placeholder="Schemaname, Schule, Schulträger, Instanz" />

      <label for="schultraeger-filter">Schulträger</label>
      <select id="schultraeger-filter" v-model="schultraegerFilter" @change="suchen">
        <option value="">Alle</option>
        <option v-for="schultraeger in schultraegerStore.items" :key="schultraeger.id" :value="schultraeger.id">
          {{ schultraeger.name }}
        </option>
      </select>

      <label for="instanz-filter">SVWS-Instanz</label>
      <select id="instanz-filter" v-model="instanzFilter" @change="suchen">
        <option value="">Alle</option>
        <option v-for="instanz in svwsInstanzStore.items" :key="instanz.id" :value="instanz.id">
          {{ instanz.name }}
        </option>
      </select>

      <label for="umgebung-filter">Umgebung</label>
      <select id="umgebung-filter" v-model="umgebungFilter" @change="suchen">
        <option value="">Alle</option>
        <option
          v-for="umgebung in schemaUmgebungStore.items.filter((u) => u.aktiv)"
          :key="umgebung.id"
          :value="umgebung.name"
        >
          {{ umgebung.name }}
        </option>
      </select>

      <label for="status-filter">Status</label>
      <select id="status-filter" v-model="statusFilter" @change="suchen">
        <option value="">Alle</option>
        <option v-for="option in statusOptionen" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>

      <button type="submit">Suchen</button>
    </form>

    <p v-if="store.errorMessage" role="alert" class="fehler">{{ store.errorMessage }}</p>
    <p v-else-if="store.loading">Lädt …</p>

    <div v-else class="table-wrap">
      <table>
        <caption class="sr-only">
          Liste der Schuldatenbanken
        </caption>
        <thead>
          <tr>
            <th scope="col">Schulträger</th>
            <th scope="col">Schule</th>
            <th scope="col">Schemaname</th>
            <th scope="col">Umgebung</th>
            <th scope="col">SVWS-Instanz</th>
            <th scope="col">Status</th>
            <th scope="col">Aktiv/Inaktiv</th>
            <th scope="col">Aktionen</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="eintrag in store.items" :key="eintrag.id">
            <td>{{ eintrag.schultraegerName }}</td>
            <td>{{ eintrag.schulnummer }} – {{ eintrag.schuleName }}</td>
            <td>{{ eintrag.schemaName }}</td>
            <td>{{ eintrag.umgebung }}</td>
            <td>{{ eintrag.instanzName }}</td>
            <td>
              <span :class="['status', `status-${eintrag.status.toLowerCase()}`]">{{ eintrag.status }}</span>
            </td>
            <td>
              <span :class="['status', eintrag.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                {{ eintrag.aktiv ? 'Aktiv' : 'Deaktiviert' }}
              </span>
            </td>
            <td>
              <RouterLink
                :to="{
                  name: 'schule-detail',
                  params: { schultraegerId: eintrag.schultraegerId, schuleId: eintrag.schuleId },
                }"
                class="button-secondary btn-klein"
              >
                Zur Schule
              </RouterLink>
            </td>
          </tr>
          <tr v-if="store.items.length === 0">
            <td colspan="8">Keine Schuldatenbanken gefunden.</td>
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

    <Modal :open="neueSchuleModalOffen" titel="Neue Schule anlegen" @close="neueSchuleSchliessen">
      <form @submit.prevent="neueSchuleAbsenden">
        <p v-if="neueSchuleFehler" role="alert" class="fehler">{{ neueSchuleFehler }}</p>
        <p class="hinweis">
          Die SVWS-Instanz für die erste Schuldatenbank wird im nächsten Schritt auf der Schulseite ausgewählt.
        </p>

        <div class="feld">
          <label for="neue-schule-traeger">Schulträger</label>
          <select id="neue-schule-traeger" v-model="neueSchuleSchultraegerId" required>
            <option v-for="schultraeger in aktiveSchultraeger" :key="schultraeger.id" :value="schultraeger.id">
              {{ schultraeger.name }}
            </option>
          </select>
          <p v-if="aktiveSchultraeger.length === 0" role="alert" class="fehler">
            Es gibt keinen aktiven Schulträger. Bitte zuerst einen Schulträger anlegen oder reaktivieren.
          </p>
        </div>

        <div class="feld">
          <label for="neue-schule-schulnummer">Schulnummer</label>
          <input id="neue-schule-schulnummer" v-model="neueSchuleSchulnummer" type="text" required />
        </div>

        <div class="feld">
          <label for="neue-schule-name">Name</label>
          <input id="neue-schule-name" v-model="neueSchuleName" type="text" required />
        </div>

        <div class="aktionen">
          <button
            type="submit"
            class="button-primary"
            :disabled="neueSchuleSpeichern || aktiveSchultraeger.length === 0"
          >
            Anlegen
          </button>
          <button type="button" class="button-secondary" @click="neueSchuleSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>
  </main>
</template>

<style scoped>
main {
  max-width: min(100%, 84rem);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.hinweis {
  color: var(--ink-soft);
  max-width: 48rem;
}

.hinweis a {
  color: var(--accent);
}

.search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 1rem 0;
}

.search input,
.search select {
  flex: 1 1 10rem;
  min-width: 0;
}

.search select {
  font: inherit;
  padding: 0.3rem 0.4rem;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}

.search select:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
  border-color: var(--accent);
}

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

table {
  width: 100%;
  min-width: 60rem;
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

.btn-klein {
  padding: 0.2rem 0.6rem;
  font-size: 0.85em;
}

.status {
  font-weight: 600;
  white-space: nowrap;
}

.status-aktiv {
  color: var(--accent);
}

.status-inaktiv,
.status-geplant,
.status-archiviert {
  color: var(--ink-soft);
}

.status-fehler,
.status-migration_erforderlich {
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
