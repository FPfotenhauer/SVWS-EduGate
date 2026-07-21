<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useSchuldateiStore } from '@/stores/schuldateiStore'
import { ApiError } from '@/types/problem'

/**
 * Betreiber-Einstellung "Schuldatei" (ADR-020): Landes-Schuldatei als Referenzkatalog. Zeigt
 * Schulen und Schulträger als eigene, durchsuchbare/filterbare Sichten - reine Referenzdaten,
 * kein EduGate-Betreiberbestand. Der Refresh ist eine bewusste, geschützte Aktion, kein
 * automatischer Abruf beim Öffnen der Seite.
 */
const store = useSchuldateiStore()
const aktiverTab = ref<'schulen' | 'schultraeger'>('schulen')
const refreshFehler = ref<string | null>(null)

const schulenSuchbegriff = ref('')
const schulenTraegernummer = ref('')
const schulenNurAktiveAuswahl = ref(true)

const schultraegerSuchbegriff = ref('')
const schultraegerNurAktiveAuswahl = ref(true)

onMounted(async () => {
  await store.fetchStatus()
  await store.fetchSchulen({ page: 0, nurAktive: schulenNurAktiveAuswahl.value })
})

function tabWechseln(tab: 'schulen' | 'schultraeger'): void {
  aktiverTab.value = tab
  if (tab === 'schulen' && store.schulen.length === 0) {
    void store.fetchSchulen({ page: 0, nurAktive: schulenNurAktiveAuswahl.value })
  }
  if (tab === 'schultraeger' && store.schultraeger.length === 0) {
    void store.fetchSchultraeger({ page: 0, nurAktive: schultraegerNurAktiveAuswahl.value })
  }
}

async function jetztAktualisieren(): Promise<void> {
  refreshFehler.value = null
  try {
    await store.refresh()
    // Zeigt die aktuellen Daten sofort an, statt den Betreiber zu einem manuellen Neuladen der
    // Seite zu zwingen. Lädt beide Listen (nicht nur den sichtbaren Tab) mit ihren bestehenden
    // Filtern/Seiten neu (fetchSchulen/fetchSchultraeger verwenden ohne Argumente die zuletzt
    // gesetzten Filter) - sonst zeigt ein späterer Tab-Wechsel noch den Stand vor dem Refresh,
    // weil tabWechseln() nur bei leerer Liste automatisch nachlädt.
    await Promise.all([store.fetchSchulen(), store.fetchSchultraeger()])
  } catch (error) {
    refreshFehler.value = error instanceof ApiError ? error.message : 'Aktualisierung fehlgeschlagen.'
  }
}

function schulenSuchen(): void {
  void store.fetchSchulen({
    page: 0,
    q: schulenSuchbegriff.value,
    schultraegernummer: schulenTraegernummer.value,
    nurAktive: schulenNurAktiveAuswahl.value,
  })
}

function schulenVorherigeSeite(): void {
  if (store.schulenPage > 0) {
    void store.fetchSchulen({ page: store.schulenPage - 1 })
  }
}

function schulenNaechsteSeite(): void {
  if ((store.schulenPage + 1) * store.schulenSize < store.schulenTotalElements) {
    void store.fetchSchulen({ page: store.schulenPage + 1 })
  }
}

function schultraegerSuchen(): void {
  void store.fetchSchultraeger({
    page: 0,
    q: schultraegerSuchbegriff.value,
    nurAktive: schultraegerNurAktiveAuswahl.value,
  })
}

function schultraegerVorherigeSeite(): void {
  if (store.schultraegerPage > 0) {
    void store.fetchSchultraeger({ page: store.schultraegerPage - 1 })
  }
}

function schultraegerNaechsteSeite(): void {
  if ((store.schultraegerPage + 1) * store.schultraegerSize < store.schultraegerTotalElements) {
    void store.fetchSchultraeger({ page: store.schultraegerPage + 1 })
  }
}

function formatiereZeitpunkt(iso: string | null): string {
  return iso
    ? new Date(iso).toLocaleString('de-DE', {
        day: '2-digit',
        month: '2-digit',
        year: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '–'
}
</script>

<template>
  <main>
    <header class="toolbar">
      <h1>Schuldatei</h1>
    </header>
    <p class="hinweis">
      Amtliche Landes-Schuldatei als Referenzkatalog (ADR-020): rein referenzielle Stammdaten für Suche und Abgleich,
      kein EduGate-Betreiberbestand.
    </p>

    <section class="status-bereich">
      <div v-if="store.statusLoading" class="hinweis-klein">Lädt Status …</div>
      <div v-else-if="store.status" class="status-inhalt">
        <span :class="['status', store.status.erfolgreich ? 'status-aktiv' : 'status-unreachable']">
          {{ store.status.erfolgreich ? 'Letzter Import erfolgreich' : 'Letzter Import fehlgeschlagen' }}
        </span>
        <span class="hinweis-klein">{{ formatiereZeitpunkt(store.status.gestartetAm) }}</span>
        <span v-if="store.status.erfolgreich" class="hinweis-klein">
          {{ store.status.anzahlSchulen ?? 0 }} Schulen, {{ store.status.anzahlSchultraeger ?? 0 }} Schulträger
        </span>
        <span v-else-if="store.status.fehlermeldung" class="hinweis-klein fehler">{{
          store.status.fehlermeldung
        }}</span>
        <span class="hinweis-klein">ausgelöst von {{ store.status.ausgeloestVon }}</span>
      </div>
      <div v-else class="hinweis-klein">Noch kein Import gelaufen.</div>

      <button type="button" class="button-primary" :disabled="store.refreshing" @click="jetztAktualisieren">
        {{ store.refreshing ? 'Wird aktualisiert …' : 'Jetzt aktualisieren' }}
      </button>
      <p v-if="refreshFehler" role="alert" class="fehler">{{ refreshFehler }}</p>
    </section>

    <nav class="tabs" aria-label="Ansicht wählen">
      <button type="button" :class="['tab', { 'tab-aktiv': aktiverTab === 'schulen' }]" @click="tabWechseln('schulen')">
        Schulen
      </button>
      <button
        type="button"
        :class="['tab', { 'tab-aktiv': aktiverTab === 'schultraeger' }]"
        @click="tabWechseln('schultraeger')"
      >
        Schulträger
      </button>
    </nav>

    <section v-if="aktiverTab === 'schulen'">
      <form class="search" @submit.prevent="schulenSuchen">
        <label for="schulen-suche">Suche nach Name, Schulnummer oder Ort</label>
        <input id="schulen-suche" v-model="schulenSuchbegriff" type="search" placeholder="z. B. Musterschule" />
        <label for="schulen-traeger">Schulträgernummer</label>
        <input id="schulen-traeger" v-model="schulenTraegernummer" type="text" placeholder="z. B. 10100" />
        <label class="checkbox-label">
          <input v-model="schulenNurAktiveAuswahl" type="checkbox" @change="schulenSuchen" />
          Nur aktive Schulen
        </label>
        <button type="submit">Suchen</button>
      </form>

      <p v-if="store.schulenError" role="alert" class="fehler">{{ store.schulenError }}</p>
      <p v-else-if="store.schulenLoading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Schulen aus der Landes-Schuldatei
          </caption>
          <thead>
            <tr>
              <th scope="col">Schulnummer</th>
              <th scope="col">Name</th>
              <th scope="col">Schulform</th>
              <th scope="col">Ort</th>
              <th scope="col">Schulträger</th>
              <th scope="col">Status</th>
              <th scope="col">Zuletzt gesehen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="schule in store.schulen" :key="schule.id">
              <td>{{ schule.schulnummer }}</td>
              <td>{{ schule.schulname }}</td>
              <td>{{ schule.schulform ?? '–' }}</td>
              <td>{{ schule.ort ?? '–' }}</td>
              <td>
                <span v-if="schule.schultraegername"
                  >{{ schule.schultraegername }} ({{ schule.schultraegernummer }})</span
                >
                <span v-else class="hinweis-klein">unbekannt</span>
              </td>
              <td>
                <span :class="['status', schule.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                  {{ schule.aktiv ? 'Aktiv' : 'Aufgelöst' }}
                </span>
              </td>
              <td>{{ formatiereZeitpunkt(schule.lastSeenAt) }}</td>
            </tr>
            <tr v-if="store.schulen.length === 0">
              <td colspan="7">Keine Schulen gefunden.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav class="pagination" aria-label="Seitennavigation Schulen">
        <button type="button" :disabled="store.schulenPage === 0" @click="schulenVorherigeSeite">Zurück</button>
        <span>
          Seite {{ store.schulenPage + 1 }} von
          {{ Math.max(1, Math.ceil(store.schulenTotalElements / store.schulenSize)) }} ({{ store.schulenTotalElements }}
          Schulen)
        </span>
        <button
          type="button"
          :disabled="(store.schulenPage + 1) * store.schulenSize >= store.schulenTotalElements"
          @click="schulenNaechsteSeite"
        >
          Weiter
        </button>
      </nav>
    </section>

    <section v-else>
      <form class="search" @submit.prevent="schultraegerSuchen">
        <label for="schultraeger-suche">Suche nach Name, Trägernummer oder Ort</label>
        <input
          id="schultraeger-suche"
          v-model="schultraegerSuchbegriff"
          type="search"
          placeholder="z. B. Musterträger"
        />
        <label class="checkbox-label">
          <input v-model="schultraegerNurAktiveAuswahl" type="checkbox" @change="schultraegerSuchen" />
          Nur aktive Schulträger
        </label>
        <button type="submit">Suchen</button>
      </form>

      <p v-if="store.schultraegerError" role="alert" class="fehler">{{ store.schultraegerError }}</p>
      <p v-else-if="store.schultraegerLoading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Schulträger aus der Landes-Schuldatei
          </caption>
          <thead>
            <tr>
              <th scope="col">Trägernummer</th>
              <th scope="col">Name</th>
              <th scope="col">Trägerschaftsart</th>
              <th scope="col">Ort</th>
              <th scope="col">Status</th>
              <th scope="col">Zuletzt gesehen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="traeger in store.schultraeger" :key="traeger.id">
              <td>{{ traeger.traegernummer }}</td>
              <td>{{ traeger.traegername }}</td>
              <td>{{ traeger.traegerschaftsart ?? '–' }}</td>
              <td>{{ traeger.ort ?? '–' }}</td>
              <td>
                <span :class="['status', traeger.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                  {{ traeger.aktiv ? 'Aktiv' : 'Aufgelöst' }}
                </span>
              </td>
              <td>{{ formatiereZeitpunkt(traeger.lastSeenAt) }}</td>
            </tr>
            <tr v-if="store.schultraeger.length === 0">
              <td colspan="6">Keine Schulträger gefunden.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <nav class="pagination" aria-label="Seitennavigation Schulträger">
        <button type="button" :disabled="store.schultraegerPage === 0" @click="schultraegerVorherigeSeite">
          Zurück
        </button>
        <span>
          Seite {{ store.schultraegerPage + 1 }} von
          {{ Math.max(1, Math.ceil(store.schultraegerTotalElements / store.schultraegerSize)) }}
          ({{ store.schultraegerTotalElements }} Schulträger)
        </span>
        <button
          type="button"
          :disabled="(store.schultraegerPage + 1) * store.schultraegerSize >= store.schultraegerTotalElements"
          @click="schultraegerNaechsteSeite"
        >
          Weiter
        </button>
      </nav>
    </section>
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

.status-bereich {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-strong);
}

.status-inhalt {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--line);
}

.tab {
  padding: 0.5rem 1rem;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--ink-soft);
  cursor: pointer;
  font: inherit;
}

.tab:hover {
  color: var(--ink);
}

.tab-aktiv {
  color: var(--ink);
  border-bottom-color: var(--accent);
  font-weight: 600;
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

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
  white-space: nowrap;
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
  min-width: 50rem;
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

.status {
  font-weight: 600;
  white-space: nowrap;
}

.status-aktiv {
  color: var(--accent);
}

.status-inaktiv,
.status-unreachable {
  color: var(--error);
}

.hinweis-klein {
  font-size: 0.85em;
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
