<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useSchemaStore } from '@/stores/schemaStore'
import { useSchemaUmgebungStore } from '@/stores/schemaUmgebungStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { Schema } from '@/types/schema'
import type { Schule } from '@/types/schule'
import type { Schultraeger } from '@/types/schultraeger'

// Betreiber-Arbeitsseite für eine Schule (statt einer Tabellen-Ansicht, die die Haupt-Übersicht
// "Schuldatenbanken" nur dupliziert hätte): zeigt, welche Operationen an dieser Schule möglich
// sind. Migration/Backup existieren backend-seitig noch nicht (ADR-012/013 stellen das bewusst
// zurück) und erscheinen deshalb sichtbar, aber deaktiviert - keine vorgetäuschte Funktionalität.

const props = defineProps<{ schultraegerId: string; schuleId: string }>()

const route = useRoute()
const router = useRouter()
const schuleStore = useSchuleStore()
const schultraegerStore = useSchultraegerStore()
const schemaStore = useSchemaStore()
const svwsInstanzStore = useSvwsInstanzStore()
const schemaUmgebungStore = useSchemaUmgebungStore()

const geladeneSchule = ref<Schule | null>(null)
const geladenerSchultraeger = ref<Schultraeger | null>(null)

onMounted(async () => {
  geladeneSchule.value = await schuleStore.get(props.schultraegerId, props.schuleId)
  schuleSchulnummer.value = geladeneSchule.value.schulnummer
  schuleName.value = geladeneSchule.value.name
  geladenerSchultraeger.value = await schultraegerStore.get(props.schultraegerId)
  svwsInstanzStore.size = 100
  await Promise.all([
    svwsInstanzStore.fetchList({ page: 0 }),
    schemaUmgebungStore.fetchList(),
    schemaStore.fetchList(props.schultraegerId, props.schuleId),
  ])

  // Direkt aus "Neue Schule anlegen" (Schuldatenbanken-Übersicht) kommend: die SVWS-Instanz für
  // die erste Schuldatenbank muss unmittelbar wählbar sein, statt in einem separaten Klick.
  if (route.query.schuldatenbankAnlegen === '1') {
    schemaHinzufuegenOeffnen()
  }
})

// --- Schule bearbeiten (Schulnummer/Name) -------------------------------------------------
const schuleBearbeitenModalOffen = ref(false)
const schuleSchulnummer = ref('')
const schuleName = ref('')
const schuleSpeichern = ref(false)
const schuleFehler = ref<string | null>(null)

function schuleBearbeitenOeffnen(): void {
  if (geladeneSchule.value) {
    schuleSchulnummer.value = geladeneSchule.value.schulnummer
    schuleName.value = geladeneSchule.value.name
  }
  schuleFehler.value = null
  schuleBearbeitenModalOffen.value = true
}

function schuleBearbeitenSchliessen(): void {
  schuleBearbeitenModalOffen.value = false
}

async function schuleAbsenden(): Promise<void> {
  schuleFehler.value = null
  schuleSpeichern.value = true
  try {
    geladeneSchule.value = await schuleStore.update(props.schultraegerId, props.schuleId, {
      schulnummer: schuleSchulnummer.value,
      name: schuleName.value,
    })
    schuleBearbeitenModalOffen.value = false
  } catch (error) {
    schuleFehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    schuleSpeichern.value = false
  }
}

// --- Operation "Leeres Schema erstellen": nutzt das vorhandene Schema-Anlegen-Formular ------
const instanzId = ref('')
const schemaName = ref('')
const umgebung = ref('')
const beschreibung = ref('')
const schemaSpeichern = ref(false)
const schemaFehler = ref<string | null>(null)
const schemaModalOffen = ref(false)

const namensvorschlagLaeuft = ref(false)
const namensvorschlagHinweis = ref<string | null>(null)

function schemaHinzufuegenOeffnen(): void {
  const aktiveUmgebungen = schemaUmgebungStore.items.filter((u) => u.aktiv)
  instanzId.value = svwsInstanzStore.items[0]?.id ?? ''
  schemaName.value = ''
  umgebung.value = aktiveUmgebungen.find((u) => u.name === 'PRODUKTIV')?.name ?? aktiveUmgebungen[0]?.name ?? ''
  beschreibung.value = ''
  schemaFehler.value = null
  namensvorschlagHinweis.value = null
  schemaModalOffen.value = true
}

function schemaModalSchliessen(): void {
  schemaModalOffen.value = false
}

async function namensvorschlagUebernehmen(): Promise<void> {
  namensvorschlagHinweis.value = null
  namensvorschlagLaeuft.value = true
  try {
    const vorschlag = await schemaStore.namingSuggestion(props.schultraegerId, props.schuleId, umgebung.value)
    if (vorschlag.schemaName) {
      schemaName.value = vorschlag.schemaName
    } else {
      namensvorschlagHinweis.value =
        'Keine belastbare (sechsstellige) Schulnummer hinterlegt - bitte einen technischen Schemanamen ' +
        'explizit vergeben, statt ihn zu erraten.'
    }
  } catch (error) {
    namensvorschlagHinweis.value = error instanceof ApiError ? error.message : 'Namensvorschlag fehlgeschlagen.'
  } finally {
    namensvorschlagLaeuft.value = false
  }
}

async function schemaAbsenden(): Promise<void> {
  schemaFehler.value = null
  schemaSpeichern.value = true
  try {
    await schemaStore.create(props.schultraegerId, props.schuleId, {
      instanzId: instanzId.value,
      schemaName: schemaName.value,
      umgebung: umgebung.value,
      beschreibung: beschreibung.value || undefined,
    })
    schemaModalOffen.value = false
  } catch (error) {
    schemaFehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    schemaSpeichern.value = false
  }
}

function instanzName(instanzId: string): string {
  return svwsInstanzStore.items.find((instanz) => instanz.id === instanzId)?.name ?? instanzId
}

// --- Operation "Echtes Schema über die SVWS-Instanz löschen" ---------------------------------
// Unwiderruflich (ADR-014, POST /api/schema/root/destroy/{schema}) - deshalb zusätzlich zur
// Warnung eine Bestätigung durch Eintippen des Schemanamens statt nur eines Klicks.
const schemaLoeschenModalOffen = ref(false)
const zuLoeschendesSchema = ref<Schema | null>(null)
const schemaLoeschenBestaetigungstext = ref('')
const schemaLoeschenLaeuft = ref(false)
const schemaLoeschenFehler = ref<string | null>(null)

function schemaLoeschenOeffnen(schema: Schema): void {
  zuLoeschendesSchema.value = schema
  schemaLoeschenBestaetigungstext.value = ''
  schemaLoeschenFehler.value = null
  schemaLoeschenModalOffen.value = true
}

function schemaLoeschenSchliessen(): void {
  schemaLoeschenModalOffen.value = false
}

async function schemaLoeschenBestaetigen(): Promise<void> {
  if (!zuLoeschendesSchema.value) return
  schemaLoeschenFehler.value = null
  schemaLoeschenLaeuft.value = true
  try {
    const ergebnis = await schemaStore.destroy(props.schultraegerId, props.schuleId, zuLoeschendesSchema.value.id)
    if (ergebnis.success) {
      schemaLoeschenModalOffen.value = false
    } else {
      schemaLoeschenFehler.value = ergebnis.message ?? 'Löschen fehlgeschlagen.'
    }
  } catch (error) {
    schemaLoeschenFehler.value = error instanceof ApiError ? error.message : 'Löschen fehlgeschlagen.'
  } finally {
    schemaLoeschenLaeuft.value = false
  }
}

// --- Operation "Schule deaktivieren" --------------------------------------------------------
const deaktivierenBestaetigen = ref(false)

async function bestaetigenSchuleDeaktivieren(): Promise<void> {
  geladeneSchule.value = await schuleStore.deactivate(props.schultraegerId, props.schuleId)
  deaktivierenBestaetigen.value = false
}

// --- Operation "Schule endgültig löschen" -----------------------------------------------------
// Blockiert serverseitig, solange noch echte Schuldatenbanken existieren (siehe Schema-Liste
// oben) - hier reicht daher ein normaler Bestätigungsdialog, analog zum harten Löschen von
// Schulträgern.
const loeschenBestaetigen = ref(false)
const loeschenFehler = ref<string | null>(null)

async function bestaetigenSchuleLoeschen(): Promise<void> {
  loeschenFehler.value = null
  loeschenBestaetigen.value = false
  try {
    await schuleStore.deleteEndgueltig(props.schultraegerId, props.schuleId)
    await router.push({ name: 'schuldatenbank-uebersicht' })
  } catch (error) {
    loeschenFehler.value = error instanceof ApiError ? error.message : 'Löschen fehlgeschlagen.'
  }
}
</script>

<template>
  <main>
    <h1>{{ geladeneSchule?.name ?? 'Schule' }}</h1>

    <dl v-if="geladeneSchule" class="detail-grid">
      <div class="detail-eintrag">
        <dt>Schulnummer</dt>
        <dd>{{ geladeneSchule.schulnummer }}</dd>
      </div>
      <div class="detail-eintrag">
        <dt>Schulträger</dt>
        <dd>{{ geladenerSchultraeger?.name ?? '…' }}</dd>
      </div>
      <div class="detail-eintrag">
        <dt>Status</dt>
        <dd>
          <span :class="['status', geladeneSchule.aktiv ? 'status-aktiv' : 'status-inaktiv']">
            {{ geladeneSchule.aktiv ? 'Aktiv' : 'Deaktiviert' }}
          </span>
        </dd>
      </div>
    </dl>

    <div class="aktionen schule-aktionen">
      <button type="button" class="button-secondary btn-klein" @click="schuleBearbeitenOeffnen">
        Schule bearbeiten
      </button>
    </div>

    <section class="operationen">
      <h2>Operationen</h2>

      <div class="operationen-grid">
        <article class="operation-karte">
          <h3>Leeres Schema erstellen</h3>
          <p>Legt eine neue Schuldatenbank für diese Schule auf einer SVWS-Instanz an.</p>
          <button
            type="button"
            class="button-primary"
            :disabled="!geladeneSchule?.aktiv"
            @click="schemaHinzufuegenOeffnen"
          >
            Anlegen
          </button>
          <p v-if="geladeneSchule && !geladeneSchule.aktiv" class="hinweis-klein">
            Nicht möglich - diese Schule ist deaktiviert.
          </p>
        </article>

        <article class="operation-karte">
          <h3>Migration durchführen</h3>
          <p>Daten aus einem Altsystem in ein Schema dieser Schule migrieren.</p>
          <button type="button" class="button-secondary" disabled>In Vorbereitung</button>
        </article>

        <article class="operation-karte">
          <h3>Backup</h3>
          <p>Sicherung einer Schuldatenbank erstellen oder einspielen.</p>
          <button type="button" class="button-secondary" disabled>In Vorbereitung</button>
        </article>

        <article v-if="geladeneSchule?.aktiv" class="operation-karte gefahr">
          <h3>Schule deaktivieren</h3>
          <p>
            Die Schule bleibt erhalten, ist aber nicht mehr aktiv nutzbar. Vorhandene Schuldatenbanken bleiben bestehen.
          </p>
          <button type="button" class="danger" @click="deaktivierenBestaetigen = true">Deaktivieren</button>
        </article>

        <article class="operation-karte gefahr">
          <h3>Schule endgültig löschen</h3>
          <p>
            Nur möglich, solange keine echten Schuldatenbanken mehr auf einer SVWS-Instanz existieren (siehe unten).
            Diese Aktion kann nicht rückgängig gemacht werden.
          </p>
          <p v-if="loeschenFehler" role="alert" class="fehler">{{ loeschenFehler }}</p>
          <button type="button" class="danger" @click="loeschenBestaetigen = true">Endgültig löschen</button>
        </article>
      </div>

      <p class="hinweis-klein">Weitere Operationen (z. B. Zertifikatsverwaltung, Credential-Rotation) folgen später.</p>
    </section>

    <section class="schemata">
      <h2>Schuldatenbanken</h2>

      <p v-if="schemaStore.errorMessage" role="alert" class="fehler">{{ schemaStore.errorMessage }}</p>
      <p v-else-if="schemaStore.loading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Liste der Schuldatenbanken dieser Schule
          </caption>
          <thead>
            <tr>
              <th scope="col">Schemaname</th>
              <th scope="col">Umgebung</th>
              <th scope="col">Status</th>
              <th scope="col">Herkunft</th>
              <th scope="col">SVWS-Instanz</th>
              <th scope="col">Aktionen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="schema in schemaStore.items" :key="schema.id">
              <td>{{ schema.schemaName }}</td>
              <td>{{ schema.umgebung }}</td>
              <td>
                <span :class="['status', `status-${schema.status.toLowerCase()}`]">{{ schema.status }}</span>
              </td>
              <td>{{ schema.source === 'SYNCHRONISIERT' ? 'Synchronisiert' : 'Manuell' }}</td>
              <td>{{ instanzName(schema.instanzId) }}</td>
              <td class="aktionen-zelle">
                <span v-if="schema.status === 'GEPLANT'" class="hinweis-klein">
                  Nur vorbereitet, noch nicht real angelegt.
                </span>
                <button v-else type="button" class="danger btn-klein" @click="schemaLoeschenOeffnen(schema)">
                  Über SVWS-Instanz löschen
                </button>
              </td>
            </tr>
            <tr v-if="schemaStore.items.length === 0">
              <td colspan="6">Keine Schuldatenbanken erfasst.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <Modal :open="schuleBearbeitenModalOffen" titel="Schule bearbeiten" @close="schuleBearbeitenSchliessen">
      <form @submit.prevent="schuleAbsenden">
        <p v-if="schuleFehler" role="alert" class="fehler">{{ schuleFehler }}</p>

        <div class="feld">
          <label for="schule-schulnummer">Schulnummer</label>
          <input id="schule-schulnummer" v-model="schuleSchulnummer" type="text" required />
        </div>

        <div class="feld">
          <label for="schule-name">Name</label>
          <input id="schule-name" v-model="schuleName" type="text" required />
        </div>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="schuleSpeichern">Aktualisieren</button>
          <button type="button" class="button-secondary" @click="schuleBearbeitenSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <Modal :open="schemaModalOffen" titel="Neue Schuldatenbank anlegen" @close="schemaModalSchliessen">
      <form @submit.prevent="schemaAbsenden">
        <p v-if="schemaFehler" role="alert" class="fehler">{{ schemaFehler }}</p>

        <div class="feld">
          <label for="schema-instanz">SVWS-Instanz</label>
          <select id="schema-instanz" v-model="instanzId" required>
            <option v-for="instanz in svwsInstanzStore.items" :key="instanz.id" :value="instanz.id">
              {{ instanz.name }}
            </option>
          </select>
        </div>

        <div class="feld">
          <label for="schema-umgebung">Umgebung</label>
          <select id="schema-umgebung" v-model="umgebung" required>
            <option v-for="u in schemaUmgebungStore.items.filter((item) => item.aktiv)" :key="u.id" :value="u.name">
              {{ u.name }}
            </option>
          </select>
        </div>

        <div class="feld">
          <label for="schema-name">Schemaname</label>
          <div class="schema-name-zeile">
            <input id="schema-name" v-model="schemaName" type="text" required />
            <button
              type="button"
              class="button-secondary btn-klein"
              :disabled="namensvorschlagLaeuft"
              @click="namensvorschlagUebernehmen"
            >
              Vorschlag übernehmen
            </button>
          </div>
          <p v-if="namensvorschlagHinweis" class="hinweis-klein">{{ namensvorschlagHinweis }}</p>
        </div>

        <div class="feld">
          <label for="schema-beschreibung">Beschreibung (optional)</label>
          <textarea id="schema-beschreibung" v-model="beschreibung" rows="2"></textarea>
        </div>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="schemaSpeichern">Anlegen</button>
          <button type="button" class="button-secondary" @click="schemaModalSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="deaktivierenBestaetigen"
      titel="Schule deaktivieren"
      :nachricht="`Soll '${geladeneSchule?.name}' wirklich deaktiviert werden?`"
      @confirm="bestaetigenSchuleDeaktivieren"
      @cancel="deaktivierenBestaetigen = false"
    />

    <ConfirmDialog
      :open="loeschenBestaetigen"
      titel="Schule endgültig löschen"
      :nachricht="`Soll '${geladeneSchule?.name}' unwiderruflich gelöscht werden? Diese Aktion kann nicht rückgängig gemacht werden.`"
      bestaetigen-text="Endgültig löschen"
      @confirm="bestaetigenSchuleLoeschen"
      @cancel="loeschenBestaetigen = false"
    />

    <Modal
      :open="schemaLoeschenModalOffen"
      titel="Schema über die SVWS-Instanz löschen"
      @close="schemaLoeschenSchliessen"
    >
      <p role="alert" class="fehler-warnung">
        Achtung: Dies löscht das Schema <strong>{{ zuLoeschendesSchema?.schemaName }}</strong> unwiderruflich auf der
        SVWS-Instanz <strong>{{ zuLoeschendesSchema ? instanzName(zuLoeschendesSchema.instanzId) : '' }}</strong
        >. Stellen Sie vorher unbedingt sicher, dass ein aktuelles Backup dieser Schuldatenbank vorhanden ist - diese
        Aktion kann nicht rückgängig gemacht werden.
      </p>

      <p v-if="schemaLoeschenFehler" role="alert" class="fehler">{{ schemaLoeschenFehler }}</p>

      <form @submit.prevent="schemaLoeschenBestaetigen">
        <div class="feld">
          <label for="schema-loeschen-bestaetigung">
            Zur Bestätigung bitte den Schemanamen "{{ zuLoeschendesSchema?.schemaName }}" eingeben
          </label>
          <input id="schema-loeschen-bestaetigung" v-model="schemaLoeschenBestaetigungstext" type="text" required />
        </div>

        <div class="aktionen">
          <button
            type="submit"
            class="danger"
            :disabled="schemaLoeschenLaeuft || schemaLoeschenBestaetigungstext !== zuLoeschendesSchema?.schemaName"
          >
            Endgültig löschen
          </button>
          <button type="button" class="button-secondary" @click="schemaLoeschenSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>
  </main>
</template>

<style scoped>
main {
  max-width: min(100%, 60rem);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1rem 2rem;
  margin: 0 0 1.5rem;
  max-width: 40rem;
}

.detail-eintrag {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
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

.schema-name-zeile {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.schema-name-zeile input {
  flex: 1 1 auto;
  min-width: 0;
}

.aktionen {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-top: 1rem;
}

.schule-aktionen {
  margin-top: 0;
  margin-bottom: 1.5rem;
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

.button-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-klein {
  padding: 0.2rem 0.6rem;
  font-size: 0.85em;
}

.operationen {
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

.hinweis-klein {
  font-size: 0.85em;
  color: var(--ink-soft);
}

.fehler {
  color: var(--error);
}

.fehler-warnung {
  color: var(--error);
  border: 1px solid var(--error);
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.schemata {
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--line);
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

.aktionen-zelle {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.4rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
