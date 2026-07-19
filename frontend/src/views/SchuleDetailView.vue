<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useSchemaStore } from '@/stores/schemaStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import { STANDARD_UMGEBUNGEN, type Schema, type SchemaStatus } from '@/types/schema'
import type { Schule } from '@/types/schule'

const props = defineProps<{ schultraegerId: string; schuleId: string }>()

const schuleStore = useSchuleStore()
const schemaStore = useSchemaStore()
const svwsInstanzStore = useSvwsInstanzStore()

const geladeneSchule = ref<Schule | null>(null)

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
  geladeneSchule.value = await schuleStore.get(props.schultraegerId, props.schuleId)
  await schemaStore.fetchList(props.schultraegerId, props.schuleId)
  await svwsInstanzStore.fetchList({ page: 0 })
})

function instanzName(instanzId: string): string {
  const instanz = svwsInstanzStore.items.find((item) => item.id === instanzId)
  return instanz ? instanz.name : instanzId
}

// --- Schema/Schuldatenbank: ein Formular für Anlegen und Bearbeiten, in einem Modal -----------
const instanzId = ref('')
const schemaName = ref('')
const umgebung = ref('')
const beschreibung = ref('')
const status = ref<SchemaStatus>('GEPLANT')
const aktiv = ref(true)
const schemaBearbeiteId = ref<string | null>(null)
const schemaSpeichern = ref(false)
const schemaFehler = ref<string | null>(null)
const schemaModalOffen = ref(false)

const namensvorschlagLaeuft = ref(false)
const namensvorschlagHinweis = ref<string | null>(null)

function schemaFormularZuruecksetzen(): void {
  schemaBearbeiteId.value = null
  instanzId.value = svwsInstanzStore.items[0]?.id ?? ''
  schemaName.value = ''
  umgebung.value = 'PRODUKTIV'
  beschreibung.value = ''
  status.value = 'GEPLANT'
  aktiv.value = true
  schemaFehler.value = null
  namensvorschlagHinweis.value = null
}

function schemaHinzufuegenOeffnen(): void {
  schemaFormularZuruecksetzen()
  schemaModalOffen.value = true
}

function schemaBearbeiten(schema: Schema): void {
  schemaBearbeiteId.value = schema.id
  instanzId.value = schema.instanzId
  schemaName.value = schema.schemaName
  umgebung.value = schema.umgebung
  beschreibung.value = schema.beschreibung ?? ''
  status.value = schema.status
  aktiv.value = schema.aktiv
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
    if (schemaBearbeiteId.value) {
      await schemaStore.update(props.schultraegerId, props.schuleId, schemaBearbeiteId.value, {
        instanzId: instanzId.value,
        schemaName: schemaName.value,
        umgebung: umgebung.value,
        beschreibung: beschreibung.value || undefined,
        status: status.value,
        aktiv: aktiv.value,
      })
    } else {
      await schemaStore.create(props.schultraegerId, props.schuleId, {
        instanzId: instanzId.value,
        schemaName: schemaName.value,
        umgebung: umgebung.value,
        beschreibung: beschreibung.value || undefined,
      })
    }
    schemaModalOffen.value = false
  } catch (error) {
    schemaFehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    schemaSpeichern.value = false
  }
}

const zuDeaktivierendesSchema = ref<Schema | null>(null)

async function bestaetigenSchemaDeaktivieren(): Promise<void> {
  if (!zuDeaktivierendesSchema.value) return
  await schemaStore.deactivate(props.schultraegerId, props.schuleId, zuDeaktivierendesSchema.value.id)
  zuDeaktivierendesSchema.value = null
}
</script>

<template>
  <main>
    <p class="breadcrumb">
      <RouterLink :to="{ name: 'schultraeger-bearbeiten', params: { id: props.schultraegerId } }">
        ← Zurück zum Schulträger
      </RouterLink>
    </p>

    <h1>Schuldatenbanken{{ geladeneSchule ? ' – ' + geladeneSchule.name : '' }}</h1>

    <dl v-if="geladeneSchule" class="detail-grid">
      <div class="detail-eintrag">
        <dt>Schulnummer</dt>
        <dd>{{ geladeneSchule.schulnummer }}</dd>
      </div>
      <div class="detail-eintrag">
        <dt>Name</dt>
        <dd>{{ geladeneSchule.name }}</dd>
      </div>
    </dl>

    <section class="schemata">
      <p v-if="schemaStore.errorMessage" role="alert" class="fehler">{{ schemaStore.errorMessage }}</p>
      <p v-else-if="schemaStore.loading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Liste der Schuldatenbanken
          </caption>
          <thead>
            <tr>
              <th scope="col">Schemaname</th>
              <th scope="col">Umgebung</th>
              <th scope="col">SVWS-Instanz</th>
              <th scope="col">Status</th>
              <th scope="col">Herkunft</th>
              <th scope="col">Aktiv/Inaktiv</th>
              <th scope="col">Aktionen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="schema in schemaStore.items" :key="schema.id">
              <td>{{ schema.schemaName }}</td>
              <td>{{ schema.umgebung }}</td>
              <td>{{ instanzName(schema.instanzId) }}</td>
              <td>
                <span :class="['status', `status-${schema.status.toLowerCase()}`]">{{ schema.status }}</span>
              </td>
              <td>{{ schema.source === 'MANUELL' ? 'Manuell' : 'Synchronisiert' }}</td>
              <td>
                <span :class="['status', schema.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                  {{ schema.aktiv ? 'Aktiv' : 'Deaktiviert' }}
                </span>
              </td>
              <td class="aktionen-zelle">
                <button type="button" class="button-secondary btn-klein" @click="schemaBearbeiten(schema)">
                  Bearbeiten
                </button>
                <button
                  v-if="schema.aktiv"
                  type="button"
                  class="danger btn-klein"
                  @click="zuDeaktivierendesSchema = schema"
                >
                  Deaktivieren
                </button>
              </td>
            </tr>
            <tr v-if="schemaStore.items.length === 0">
              <td colspan="7">Keine Schuldatenbanken erfasst.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="aktionen">
        <button type="button" class="button-primary" @click="schemaHinzufuegenOeffnen">Schuldatenbank anlegen</button>
      </div>
    </section>

    <Modal
      :open="schemaModalOffen"
      :titel="schemaBearbeiteId ? 'Schuldatenbank bearbeiten' : 'Neue Schuldatenbank anlegen'"
      @close="schemaModalSchliessen"
    >
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
          <input id="schema-umgebung" v-model="umgebung" type="text" list="umgebung-vorschlaege" required />
          <datalist id="umgebung-vorschlaege">
            <option v-for="wert in STANDARD_UMGEBUNGEN" :key="wert" :value="wert" />
          </datalist>
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

        <template v-if="schemaBearbeiteId">
          <div class="feld">
            <label for="schema-status">Status</label>
            <select id="schema-status" v-model="status">
              <option v-for="option in statusOptionen" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

          <div class="feld feld-checkbox">
            <label for="schema-aktiv">
              <input id="schema-aktiv" v-model="aktiv" type="checkbox" />
              Aktiv
            </label>
          </div>
        </template>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="schemaSpeichern">
            {{ schemaBearbeiteId ? 'Aktualisieren' : 'Anlegen' }}
          </button>
          <button type="button" class="button-secondary" @click="schemaModalSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="zuDeaktivierendesSchema !== null"
      titel="Schuldatenbank deaktivieren"
      :nachricht="`Soll die Schuldatenbank '${zuDeaktivierendesSchema?.schemaName}' wirklich deaktiviert werden? Dies ist eine gefährliche Operation (ADR-012).`"
      @confirm="bestaetigenSchemaDeaktivieren"
      @cancel="zuDeaktivierendesSchema = null"
    />
  </main>
</template>

<style scoped>
main {
  max-width: min(100%, 84rem);
}

.breadcrumb {
  margin-bottom: 0.5rem;
}

.breadcrumb a {
  color: var(--ink-soft);
  text-decoration: none;
}

.breadcrumb a:hover {
  color: var(--accent);
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

.feld-checkbox label {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
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

.aktionen-zelle {
  display: flex;
  flex-wrap: nowrap;
  gap: 0.4rem;
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

.hinweis-klein {
  font-size: 0.85em;
  color: var(--ink-soft);
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
</style>
