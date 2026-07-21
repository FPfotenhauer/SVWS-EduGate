<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { useSvwsSchemaFundStore } from '@/stores/svwsSchemaFundStore'
import { ApiError } from '@/types/problem'
import type { InstanzStatus, SvwsInstanz } from '@/types/svwsInstanz'
import type { SchemaFundZuordnungsStatus } from '@/types/svwsSchemaFund'

// Nebenläufigkeit und Mindestabstand für den automatischen Verbindungstest beim Öffnen der
// Seite: begrenzt, um den "operator"-Connection-Pool (Quarkus-Default max. 20) nicht durch
// viele gleichzeitige Tests zu erschöpfen, und überspringt kürzlich getestete Instanzen, damit
// wiederholtes Neuladen nicht jedes Mal alle Instanzen erneut (und auditiert) testet.
const AUTO_TEST_NEBENLAEUFIGKEIT = 4
const AUTO_TEST_MINDESTABSTAND_MS = 5 * 60 * 1000

const store = useSvwsInstanzStore()
const schemaFundStore = useSvwsSchemaFundStore()
const suchbegriff = ref('')
const statusFilterAuswahl = ref<InstanzStatus | ''>('')
const testendeIds = ref<Record<string, boolean>>({})
const testFehler = ref<Record<string, string>>({})

// Aufklappbarer Schema-Funde-Bereich je Instanz (ADR-013: "vollständige Liste ... über Filter,
// Suche, aufklappbare Bereiche oder eine Instanz-Detailansicht zugänglich" statt Megatabelle).
// Lädt beim ersten Aufklappen die gespeicherten Sync-/Fund-Daten (ADR-012: "Nutze vorhandene
// Sync-/Fund-Daten, falls vorhanden, statt direkt im Frontend gegen SVWS zu sprechen") - der
// Store selbst spricht nur beim expliziten "Jetzt synchronisieren" gegen die Privileged-API.
const aufgeklappteIds = ref<Record<string, boolean>>({})

async function fundeUmschalten(instanz: SvwsInstanz): Promise<void> {
  const zuvorOffen = aufgeklappteIds.value[instanz.id]
  aufgeklappteIds.value = { ...aufgeklappteIds.value, [instanz.id]: !zuvorOffen }
  if (!zuvorOffen && !schemaFundStore.itemsByInstanz[instanz.id]) {
    await schemaFundStore.fetchFunde(instanz.id)
  }
}

async function syncStarten(instanz: SvwsInstanz): Promise<void> {
  try {
    await schemaFundStore.sync(instanz.id)
  } catch {
    // Fehler wird bereits im Store als errorByInstanz[instanz.id] gehalten und dort angezeigt.
  }
}

const zuordnungsStatusLabel: Record<SchemaFundZuordnungsStatus, string> = {
  BEKANNT: 'Bekannt',
  UNZUGEORDNET: 'Unzugeordnet',
  KONFLIKT: 'Konflikt',
}

const zuordnungsStatusKlasse: Record<SchemaFundZuordnungsStatus, string> = {
  BEKANNT: 'status-aktiv',
  UNZUGEORDNET: 'status-degraded',
  KONFLIKT: 'status-unreachable',
}

function formatiereFlag(value: boolean | null): string {
  if (value === null) return '–'
  return value ? 'Ja' : 'Nein'
}

// Auswahl je Instanz-ID, seitenübergreifend - Vorbereitung für spätere Massenverwaltung
// (Arbeitsauftrag: Batch-Verbindungstest, Bulk-Credential-/Schema-Import). Aktuell noch ohne
// eigene Aktion, nur die Auswahl-UI.
const ausgewaehlt = ref<Record<string, boolean>>({})

const alleAufSeiteAusgewaehlt = computed(
  () => store.items.length > 0 && store.items.every((instanz) => ausgewaehlt.value[instanz.id]),
)
const teilweiseAusgewaehlt = computed(
  () => !alleAufSeiteAusgewaehlt.value && store.items.some((instanz) => ausgewaehlt.value[instanz.id]),
)

function alleAufSeiteUmschalten(): void {
  const neuerWert = !alleAufSeiteAusgewaehlt.value
  for (const instanz of store.items) {
    ausgewaehlt.value[instanz.id] = neuerWert
  }
}

const statusLabel: Record<string, string> = { OK: 'OK', DEGRADED: 'OK', UNREACHABLE: 'N/A' }

// Liste neu laden und anschließend die neu sichtbaren Instanzen automatisch (mit)testen - auch
// bei Filter-/Seitenwechsel kann darunter eine Instanz sein, die inzwischen nicht mehr
// erreichbar ist. Nutzt dieselben Schutzmechanismen wie beim initialen Laden.
async function listeNeuLadenUndAutomatischTesten(
  optionen: { page?: number; q?: string; status?: InstanzStatus | '' } = {},
): Promise<void> {
  await store.fetchList(optionen)
  void alleAutomatischTesten()
}

onMounted(() => listeNeuLadenUndAutomatischTesten())

function suchen(): void {
  void listeNeuLadenUndAutomatischTesten({ page: 0, q: suchbegriff.value, status: statusFilterAuswahl.value })
}

function naechsteSeite(): void {
  if ((store.page + 1) * store.size < store.totalElements) {
    void listeNeuLadenUndAutomatischTesten({ page: store.page + 1 })
  }
}

function vorherigeSeite(): void {
  if (store.page > 0) {
    void listeNeuLadenUndAutomatischTesten({ page: store.page - 1 })
  }
}

async function verbindungstestStarten(instanz: SvwsInstanz): Promise<void> {
  testFehler.value = { ...testFehler.value, [instanz.id]: '' }
  testendeIds.value[instanz.id] = true
  try {
    await store.testConnection(instanz.id)
  } catch (error) {
    testFehler.value = {
      ...testFehler.value,
      [instanz.id]: error instanceof ApiError ? error.message : 'Verbindungstest fehlgeschlagen.',
    }
  } finally {
    delete testendeIds.value[instanz.id]
  }
}

// Automatischer Verbindungstest für die aktuell sichtbaren Instanzen (initiales Laden,
// Suche/Filter, Seitenwechsel): sonst sähe man z. B. bei 20 Instanzen eine nicht erreichbare
// erst, nachdem jemand manuell auf "Testen" klickt. Läuft mit begrenzter Nebenläufigkeit statt
// alle auf einmal (siehe Konstanten oben) und lässt kürzlich getestete Instanzen aus.
async function alleAutomatischTesten(): Promise<void> {
  const kandidaten = store.items.filter((instanz) => {
    if (!instanz.lastConnectionTestAt) return true
    return Date.now() - new Date(instanz.lastConnectionTestAt).getTime() > AUTO_TEST_MINDESTABSTAND_MS
  })

  let naechsterIndex = 0
  async function worker(): Promise<void> {
    while (naechsterIndex < kandidaten.length) {
      const instanz = kandidaten[naechsterIndex++]
      await verbindungstestStarten(instanz)
    }
  }

  await Promise.all(Array.from({ length: AUTO_TEST_NEBENLAEUFIGKEIT }, worker))
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

// Farbe für den letzten Verbindungstest: grün bei vollständig geprüften, gültigen Zugangsdaten;
// orange bei erfolgreicher Erreichbarkeitsprüfung ohne (geprüfte) Zugangsdaten; rot bei Fehlschlag.
function verbindungstestFarbe(instanz: SvwsInstanz): string {
  if (instanz.lastConnectionTestSuccess === false) return 'status-unreachable'
  return instanz.credentialsHinterlegt ? 'status-aktiv' : 'status-degraded'
}
</script>

<template>
  <main>
    <header class="toolbar">
      <h1>SVWS-Instanzen</h1>
      <RouterLink :to="{ name: 'svws-instanz-neu' }" class="button-primary">Neue Instanz anlegen</RouterLink>
    </header>

    <form class="search" @submit.prevent="suchen">
      <label for="suche">Suche nach Name, Base-URL oder Beschreibung</label>
      <input id="suche" v-model="suchbegriff" type="search" placeholder="z. B. svws.example.org" />
      <label for="status-filter">Status</label>
      <select id="status-filter" v-model="statusFilterAuswahl" @change="suchen">
        <option value="">Alle</option>
        <option value="OK">OK</option>
        <option value="DEGRADED">Beeinträchtigt</option>
        <option value="UNREACHABLE">Nicht erreichbar</option>
      </select>
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
            <th scope="col">Base-URL</th>
            <th scope="col">Beschreibung</th>
            <th scope="col">Status</th>
            <th scope="col">Zugangsdaten</th>
            <th scope="col">Letzter Verbindungstest</th>
            <th scope="col">Aktiv/Inaktiv</th>
            <th scope="col">Aktionen</th>
            <th scope="col">SVWS-Schemata</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="instanz in store.items" :key="instanz.id">
            <tr>
              <td class="checkbox-zelle">
                <input v-model="ausgewaehlt[instanz.id]" type="checkbox" :aria-label="`'${instanz.name}' auswählen`" />
              </td>
              <td class="id-zelle" :title="instanz.id">{{ instanz.id }}</td>
              <td>{{ instanz.name }}</td>
              <td>{{ instanz.baseUrl }}</td>
              <td class="beschreibung-zelle" :title="instanz.beschreibung ?? ''">
                {{ instanz.beschreibung ?? '–' }}
              </td>
              <td>
                <span :class="['status', `status-${instanz.status.toLowerCase()}`]">
                  {{ statusLabel[instanz.status] ?? instanz.status }}
                </span>
              </td>
              <td>
                <span
                  v-if="instanz.credentialsHinterlegt"
                  :class="['status', instanz.lastConnectionTestSuccess === false ? 'status-degraded' : 'status-aktiv']"
                >
                  {{ formatiereZeitpunkt(instanz.credentialsUpdatedAt) }}
                </span>
                <span v-else class="status status-unreachable" title="Keine Zugangsdaten hinterlegt">✗</span>
              </td>
              <td>
                <div class="zelle-inline">
                  <button
                    type="button"
                    class="btn-klein"
                    :disabled="testendeIds[instanz.id]"
                    :title="
                      instanz.credentialsHinterlegt
                        ? ''
                        : 'Keine Zugangsdaten hinterlegt - prüft nur Basis-Erreichbarkeit'
                    "
                    @click="verbindungstestStarten(instanz)"
                  >
                    {{ testendeIds[instanz.id] ? 'Teste …' : 'Testen' }}
                  </button>
                  <span v-if="testFehler[instanz.id]" role="alert" class="fehler hinweis-klein">
                    {{ testFehler[instanz.id] }}
                  </span>
                  <span
                    v-else-if="instanz.lastConnectionTestAt"
                    :class="['status', verbindungstestFarbe(instanz)]"
                    :title="instanz.lastConnectionTestMessage ?? ''"
                  >
                    {{ formatiereZeitpunkt(instanz.lastConnectionTestAt) }}
                  </span>
                  <span v-else class="hinweis-klein">Kein Test</span>
                </div>
              </td>
              <td>
                <span :class="['status', instanz.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                  {{ instanz.aktiv ? 'Aktiv' : 'Deaktiviert' }}
                </span>
              </td>
              <td>
                <RouterLink
                  :to="{ name: 'svws-instanz-bearbeiten', params: { id: instanz.id } }"
                  class="button-secondary"
                >
                  Bearbeiten
                </RouterLink>
              </td>
              <td>
                <button type="button" class="btn-klein" @click="fundeUmschalten(instanz)">
                  {{ aufgeklappteIds[instanz.id] ? 'Ausblenden' : 'Anzeigen' }}
                </button>
              </td>
            </tr>
            <tr v-if="aufgeklappteIds[instanz.id]" class="funde-zeile">
              <td colspan="11">
                <div class="funde-bereich">
                  <div class="funde-kopf">
                    <h2>SVWS-Schemata auf „{{ instanz.name }}“</h2>
                    <div class="zelle-inline">
                      <button
                        type="button"
                        class="btn-klein"
                        :disabled="schemaFundStore.syncingByInstanz[instanz.id]"
                        @click="syncStarten(instanz)"
                      >
                        {{
                          schemaFundStore.syncingByInstanz[instanz.id] ? 'Synchronisiere …' : 'Jetzt synchronisieren'
                        }}
                      </button>
                      <span
                        v-if="schemaFundStore.lastSyncResultByInstanz[instanz.id]"
                        :class="[
                          'status',
                          schemaFundStore.lastSyncResultByInstanz[instanz.id]?.success
                            ? 'status-aktiv'
                            : 'status-unreachable',
                        ]"
                      >
                        {{ schemaFundStore.lastSyncResultByInstanz[instanz.id]?.message }}
                      </span>
                    </div>
                  </div>

                  <p v-if="schemaFundStore.errorByInstanz[instanz.id]" role="alert" class="fehler">
                    {{ schemaFundStore.errorByInstanz[instanz.id] }}
                  </p>
                  <p v-else-if="schemaFundStore.loadingByInstanz[instanz.id]">Lädt …</p>
                  <p v-else-if="(schemaFundStore.itemsByInstanz[instanz.id]?.length ?? 0) === 0" class="hinweis-klein">
                    Keine gespeicherten Sync-Funde. Auf „Jetzt synchronisieren“ klicken, um die SVWS-Instanz abzufragen.
                  </p>
                  <div v-else class="table-wrap">
                    <table class="funde-tabelle">
                      <caption class="sr-only">
                        SVWS-Schemata der Instanz „{{
                          instanz.name
                        }}“
                      </caption>
                      <thead>
                        <tr>
                          <th scope="col">Schemaname</th>
                          <th scope="col">Benutzername</th>
                          <th scope="col">Revision</th>
                          <th scope="col">SVWS</th>
                          <th scope="col">In Config</th>
                          <th scope="col">Deaktiviert</th>
                          <th scope="col">Tainted</th>
                          <th scope="col">Zuletzt gesehen</th>
                          <th scope="col">Zuordnung</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="fund in schemaFundStore.itemsByInstanz[instanz.id]" :key="fund.id">
                          <td>{{ fund.schemaName }}</td>
                          <td>{{ fund.username }}</td>
                          <td>{{ fund.revision ?? '–' }}</td>
                          <td>{{ formatiereFlag(fund.isSvws) }}</td>
                          <td>{{ formatiereFlag(fund.isInConfig) }}</td>
                          <td>{{ formatiereFlag(fund.isDeactivated) }}</td>
                          <td>{{ formatiereFlag(fund.isTainted) }}</td>
                          <td>{{ formatiereZeitpunkt(fund.lastSeenAt) }}</td>
                          <td>
                            <span :class="['status', zuordnungsStatusKlasse[fund.zuordnungsStatus]]">
                              {{ zuordnungsStatusLabel[fund.zuordnungsStatus] }}
                            </span>
                            <RouterLink
                              v-if="fund.schuleId && fund.schultraegerId"
                              :to="{
                                name: 'schule-detail',
                                params: { schultraegerId: fund.schultraegerId, schuleId: fund.schuleId },
                              }"
                              class="hinweis-klein"
                            >
                              Zur Schule
                            </RouterLink>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="store.items.length === 0">
            <td colspan="11">Keine SVWS-Instanzen gefunden.</td>
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
/* Diese Ansicht hat mehr Spalten als die übrigen Listen und braucht daher mehr Breite als
   der globale main-Rahmen (60rem, style.css) vorgibt. */
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

.funde-zeile {
  background: var(--surface-strong);
}

.funde-bereich {
  padding: 0.75rem 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.funde-kopf {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.funde-kopf h2 {
  margin: 0;
  font-size: 0.95rem;
}

.funde-tabelle {
  min-width: 44rem;
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
  flex: 1 1 12rem;
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

.beschreibung-zelle {
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.zelle-inline {
  display: flex;
  flex-wrap: nowrap;
  align-items: baseline;
  gap: 0.4rem;
}

.btn-klein {
  padding: 0.2rem 0.6rem;
  font-size: 0.85em;
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
