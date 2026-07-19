<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useSchemaOverviewStore } from '@/stores/schemaOverviewStore'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { InstanzStatus, SvwsInstanz } from '@/types/svwsInstanz'

// Bearbeiten-Ansicht folgt seit ADR-013 demselben Dashboard-/Kachel-Modell wie die Schulseite
// (SchuleDetailView): Informationsblock + Operationen als Kacheln (Zugangsdaten, Verbindungstest,
// Deaktivieren), darunter unverändert die Tabelle der gehosteten Schuldatenbanken. Die
// Anlegen-Ansicht (kein bestehender Datensatz) bleibt ein einfaches Formular.

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useSvwsInstanzStore()
const schemaOverviewStore = useSchemaOverviewStore()

const bearbeitenModus = !!props.id

// Anzeige-Quelle der Wahrheit im Bearbeiten-Modus; die untenstehenden Formularfelder (name,
// baseUrl, ...) werden nur beim Öffnen eines Modals daraus befüllt und dienen im Anlegen-Modus
// zusätzlich direkt als Formularfelder.
const geladeneInstanz = ref<SvwsInstanz | null>(null)

const name = ref('')
const baseUrl = ref('')
const beschreibung = ref('')
const status = ref<InstanzStatus>('OK')
const aktiv = ref(true)
const speichern = ref(false)
const fehler = ref<string | null>(null)
const bearbeitenModalOffen = ref(false)

const credentialsModalOffen = ref(false)
const credentialsUsername = ref('')
const credentialsPassword = ref('')
const credentialsSpeichern = ref(false)
const credentialsFehler = ref<string | null>(null)

const verbindungstestLaeuft = ref(false)
const verbindungstestFehler = ref<string | null>(null)

const zuDeaktivierenBestaetigen = ref(false)

const statusOptionen: { value: InstanzStatus; label: string }[] = [
  { value: 'OK', label: 'OK' },
  { value: 'DEGRADED', label: 'Beeinträchtigt' },
  { value: 'UNREACHABLE', label: 'Nicht erreichbar' },
]

function statusLabelFor(wert: InstanzStatus): string {
  return statusOptionen.find((option) => option.value === wert)?.label ?? wert
}

onMounted(async () => {
  if (props.id) {
    geladeneInstanz.value = await store.get(props.id)
    await schemaOverviewStore.fetchList({ page: 0, size: 100, instanzId: props.id })
  }
})

function bearbeitenOeffnen(): void {
  if (geladeneInstanz.value) {
    name.value = geladeneInstanz.value.name
    baseUrl.value = geladeneInstanz.value.baseUrl
    beschreibung.value = geladeneInstanz.value.beschreibung ?? ''
    status.value = geladeneInstanz.value.status
    aktiv.value = geladeneInstanz.value.aktiv
  }
  fehler.value = null
  bearbeitenModalOffen.value = true
}

function bearbeitenSchliessen(): void {
  bearbeitenModalOffen.value = false
}

async function absenden(): Promise<void> {
  fehler.value = null
  speichern.value = true
  try {
    if (props.id) {
      geladeneInstanz.value = await store.update(props.id, {
        name: name.value,
        baseUrl: baseUrl.value,
        beschreibung: beschreibung.value || undefined,
        status: status.value,
        aktiv: aktiv.value,
      })
      bearbeitenModalOffen.value = false
    } else {
      await store.create({ name: name.value, baseUrl: baseUrl.value, beschreibung: beschreibung.value || undefined })
      await router.push({ name: 'svws-instanz-liste' })
    }
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}

function credentialsOeffnen(): void {
  credentialsUsername.value = ''
  credentialsPassword.value = ''
  credentialsFehler.value = null
  credentialsModalOffen.value = true
}

function credentialsSchliessen(): void {
  credentialsModalOffen.value = false
}

async function credentialsAbsenden(): Promise<void> {
  if (!props.id) return
  credentialsFehler.value = null
  credentialsSpeichern.value = true
  try {
    await store.setCredentials(props.id, { username: credentialsUsername.value, password: credentialsPassword.value })
    geladeneInstanz.value = await store.get(props.id)
    credentialsModalOffen.value = false
  } catch (error) {
    credentialsFehler.value =
      error instanceof ApiError ? error.message : 'Zugangsdaten konnten nicht gespeichert werden.'
  } finally {
    credentialsSpeichern.value = false
  }
}

async function verbindungstestStarten(): Promise<void> {
  if (!props.id) return
  verbindungstestFehler.value = null
  verbindungstestLaeuft.value = true
  try {
    geladeneInstanz.value = await store.testConnection(props.id)
  } catch (error) {
    verbindungstestFehler.value = error instanceof ApiError ? error.message : 'Verbindungstest fehlgeschlagen.'
  } finally {
    verbindungstestLaeuft.value = false
  }
}

async function bestaetigenDeaktivieren(): Promise<void> {
  if (!props.id) return
  await store.deactivate(props.id)
  zuDeaktivierenBestaetigen.value = false
  await router.push({ name: 'svws-instanz-liste' })
}

function formatiereZeitpunkt(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('de-DE') : '–'
}
</script>

<template>
  <main>
    <template v-if="bearbeitenModus">
      <header class="toolbar">
        <h1>{{ geladeneInstanz?.name ?? 'SVWS-Instanz' }}</h1>
        <button type="button" class="button-secondary btn-klein" @click="bearbeitenOeffnen">Instanz bearbeiten</button>
      </header>

      <dl v-if="geladeneInstanz" class="detail-grid">
        <div class="detail-eintrag">
          <dt>Base-URL</dt>
          <dd>{{ geladeneInstanz.baseUrl }}</dd>
        </div>
        <div class="detail-eintrag">
          <dt>Status</dt>
          <dd>
            <span :class="['status', `status-${geladeneInstanz.status.toLowerCase()}`]">
              {{ statusLabelFor(geladeneInstanz.status) }}
            </span>
          </dd>
        </div>
        <div class="detail-eintrag">
          <dt>Aktiv/Inaktiv</dt>
          <dd>
            <span :class="['status', geladeneInstanz.aktiv ? 'status-aktiv' : 'status-inaktiv']">
              {{ geladeneInstanz.aktiv ? 'Aktiv' : 'Deaktiviert' }}
            </span>
          </dd>
        </div>
        <div class="detail-eintrag detail-eintrag-breit">
          <dt>Beschreibung</dt>
          <dd>{{ geladeneInstanz.beschreibung ?? '–' }}</dd>
        </div>
      </dl>

      <section class="operationen">
        <h2>Operationen</h2>

        <div class="operationen-grid">
          <article class="operation-karte">
            <h3>Zugangsdaten</h3>
            <p>
              Status: <strong>{{ geladeneInstanz?.credentialsHinterlegt ? 'Hinterlegt' : 'Nicht hinterlegt' }}</strong>
              <span v-if="geladeneInstanz?.credentialsHinterlegt">
                – zuletzt geändert: {{ formatiereZeitpunkt(geladeneInstanz.credentialsUpdatedAt) }}
              </span>
            </p>
            <button type="button" class="button-primary" @click="credentialsOeffnen">Zugangsdaten setzen</button>
          </article>

          <article class="operation-karte">
            <h3>Verbindungstest</h3>
            <p v-if="verbindungstestFehler" class="fehler">{{ verbindungstestFehler }}</p>
            <p v-else-if="geladeneInstanz?.lastConnectionTestAt">
              <span :class="geladeneInstanz.lastConnectionTestSuccess ? 'erfolg' : 'fehler'">
                {{ geladeneInstanz.lastConnectionTestSuccess ? 'Erfolgreich' : 'Fehlgeschlagen' }}
              </span>
              am {{ formatiereZeitpunkt(geladeneInstanz.lastConnectionTestAt) }}
              <span v-if="geladeneInstanz.lastConnectionTestMessage"
                >– {{ geladeneInstanz.lastConnectionTestMessage }}</span
              >
            </p>
            <p v-else>Noch kein Verbindungstest durchgeführt.</p>
            <button
              type="button"
              class="button-primary"
              :disabled="verbindungstestLaeuft"
              @click="verbindungstestStarten"
            >
              {{ verbindungstestLaeuft ? 'Teste …' : 'Verbindung testen' }}
            </button>
          </article>

          <article v-if="geladeneInstanz?.aktiv" class="operation-karte gefahr">
            <h3>Instanz deaktivieren</h3>
            <p>Eine deaktivierte Instanz bleibt erhalten und kann über das Bearbeiten-Formular reaktiviert werden.</p>
            <button type="button" class="danger" @click="zuDeaktivierenBestaetigen = true">Deaktivieren</button>
          </article>
        </div>
      </section>

      <section class="schemata">
        <h2>Gehostete Schuldatenbanken</h2>

        <p v-if="schemaOverviewStore.errorMessage" role="alert" class="fehler">
          {{ schemaOverviewStore.errorMessage }}
        </p>
        <p v-else-if="schemaOverviewStore.loading">Lädt …</p>

        <div v-else class="table-wrap">
          <table>
            <caption class="sr-only">
              Auf dieser Instanz gehostete Schuldatenbanken
            </caption>
            <thead>
              <tr>
                <th scope="col">Schulträger</th>
                <th scope="col">Schule</th>
                <th scope="col">Schemaname</th>
                <th scope="col">Umgebung</th>
                <th scope="col">Status</th>
                <th scope="col">Aktiv/Inaktiv</th>
                <th scope="col">Aktionen</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="eintrag in schemaOverviewStore.items" :key="eintrag.id">
                <td>{{ eintrag.schultraegerName }}</td>
                <td>{{ eintrag.schulnummer }} – {{ eintrag.schuleName }}</td>
                <td>{{ eintrag.schemaName }}</td>
                <td>{{ eintrag.umgebung }}</td>
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
              <tr v-if="schemaOverviewStore.items.length === 0">
                <td colspan="7">Keine Schuldatenbanken auf dieser Instanz.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <Modal :open="bearbeitenModalOffen" titel="Instanz bearbeiten" @close="bearbeitenSchliessen">
        <form @submit.prevent="absenden">
          <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

          <div class="feld">
            <label for="modal-name">Kurzbezeichnung</label>
            <input id="modal-name" v-model="name" type="text" required />
          </div>

          <div class="feld">
            <label for="modal-baseUrl">Base-URL</label>
            <input id="modal-baseUrl" v-model="baseUrl" type="text" placeholder="https://svws.example.org" required />
          </div>

          <div class="feld">
            <label for="modal-beschreibung">Beschreibung (optional)</label>
            <textarea
              id="modal-beschreibung"
              v-model="beschreibung"
              rows="3"
              placeholder="Umgebung, Zweck, Besonderheiten …"
            ></textarea>
          </div>

          <div class="feld">
            <label for="modal-status">Status</label>
            <select id="modal-status" v-model="status">
              <option v-for="option in statusOptionen" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div class="feld feld-checkbox">
            <label for="modal-aktiv">
              <input id="modal-aktiv" v-model="aktiv" type="checkbox" />
              Aktiv
            </label>
          </div>

          <div class="aktionen">
            <button type="submit" class="button-primary" :disabled="speichern">Speichern</button>
            <button type="button" class="button-secondary" @click="bearbeitenSchliessen">Abbrechen</button>
          </div>
        </form>
      </Modal>

      <Modal :open="credentialsModalOffen" titel="Zugangsdaten setzen" @close="credentialsSchliessen">
        <form @submit.prevent="credentialsAbsenden">
          <p v-if="credentialsFehler" role="alert" class="fehler">{{ credentialsFehler }}</p>
          <p class="hinweis">
            Gespeicherte Zugangsdaten werden aus Sicherheitsgründen nie angezeigt. Hier eingegebene Werte ersetzen die
            bisherigen Zugangsdaten vollständig.
          </p>

          <div class="feld">
            <label for="credentials-username">Benutzername</label>
            <input id="credentials-username" v-model="credentialsUsername" type="text" autocomplete="off" required />
          </div>

          <div class="feld">
            <label for="credentials-password">Passwort</label>
            <input
              id="credentials-password"
              v-model="credentialsPassword"
              type="password"
              autocomplete="new-password"
              required
            />
          </div>

          <div class="aktionen">
            <button type="submit" class="button-primary" :disabled="credentialsSpeichern">Speichern</button>
            <button type="button" class="button-secondary" @click="credentialsSchliessen">Abbrechen</button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        :open="zuDeaktivierenBestaetigen"
        titel="SVWS-Instanz deaktivieren"
        :nachricht="`Soll '${geladeneInstanz?.name}' wirklich deaktiviert werden?`"
        @confirm="bestaetigenDeaktivieren"
        @cancel="zuDeaktivierenBestaetigen = false"
      />
    </template>

    <form v-else @submit.prevent="absenden">
      <h1>Neue SVWS-Instanz anlegen</h1>
      <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

      <div class="feld">
        <label for="name">Kurzbezeichnung</label>
        <input id="name" v-model="name" type="text" required />
      </div>

      <div class="feld">
        <label for="baseUrl">Base-URL</label>
        <input id="baseUrl" v-model="baseUrl" type="text" placeholder="https://svws.example.org" required />
      </div>

      <div class="feld">
        <label for="beschreibung">Beschreibung (optional)</label>
        <textarea
          id="beschreibung"
          v-model="beschreibung"
          rows="3"
          placeholder="Umgebung, Zweck, Besonderheiten …"
        ></textarea>
      </div>

      <div class="aktionen">
        <button type="submit" class="button-primary" :disabled="speichern">Speichern</button>
        <RouterLink :to="{ name: 'svws-instanz-liste' }">Abbrechen</RouterLink>
      </div>
    </form>
  </main>
</template>

<style scoped>
main {
  max-width: min(100%, 84rem);
}

.feld {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 1rem;
  max-width: 24rem;
}

.feld select,
.feld textarea {
  font: inherit;
  padding: 0.4rem;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
}

.feld select {
  cursor: pointer;
}

.feld select:focus-visible,
.feld textarea:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
  border-color: var(--accent);
}

.feld-checkbox label {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.aktionen {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.fehler {
  color: var(--error);
}

.erfolg {
  color: var(--accent);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1rem 2rem;
  margin: 0 0 1.5rem;
  max-width: 60rem;
}

.detail-eintrag {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.detail-eintrag-breit {
  grid-column: 1 / -1;
}

.detail-eintrag dt {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--ink-soft);
}

.detail-eintrag dd {
  margin: 0;
}

.operationen,
.schemata {
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--line);
}

.operationen-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 1rem;
  margin: 1rem 0;
}

.operation-karte {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.operation-karte h3 {
  margin: 0;
  font-size: 1rem;
}

.operation-karte p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 0.9rem;
  flex-grow: 1;
}

.operation-karte.gefahr {
  border-color: var(--error);
}

.schemata a {
  color: var(--accent);
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
  min-width: 55rem;
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

.btn-klein {
  padding: 0.2rem 0.6rem;
  font-size: 0.85em;
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

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}

.hinweis {
  color: var(--ink-soft);
  max-width: 48rem;
}
</style>
