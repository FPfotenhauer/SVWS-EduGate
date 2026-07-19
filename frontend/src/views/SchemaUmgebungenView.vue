<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useSchemaUmgebungStore } from '@/stores/schemaUmgebungStore'
import { ApiError } from '@/types/problem'
import type { SchemaUmgebung } from '@/types/schemaUmgebung'

const store = useSchemaUmgebungStore()

onMounted(() => store.fetchList())

const name = ref('')
const beschreibung = ref('')
const aktiv = ref(true)
const bearbeiteId = ref<string | null>(null)
const bearbeiteIstSystem = ref(false)
const speichern = ref(false)
const fehler = ref<string | null>(null)
const modalOffen = ref(false)

function formularZuruecksetzen(): void {
  bearbeiteId.value = null
  bearbeiteIstSystem.value = false
  name.value = ''
  beschreibung.value = ''
  aktiv.value = true
  fehler.value = null
}

function neuOeffnen(): void {
  formularZuruecksetzen()
  modalOffen.value = true
}

function bearbeiten(umgebung: SchemaUmgebung): void {
  bearbeiteId.value = umgebung.id
  bearbeiteIstSystem.value = umgebung.system
  name.value = umgebung.name
  beschreibung.value = umgebung.beschreibung ?? ''
  aktiv.value = umgebung.aktiv
  fehler.value = null
  modalOffen.value = true
}

function modalSchliessen(): void {
  modalOffen.value = false
}

async function absenden(): Promise<void> {
  fehler.value = null
  speichern.value = true
  try {
    if (bearbeiteId.value) {
      await store.update(bearbeiteId.value, {
        name: name.value,
        beschreibung: beschreibung.value || undefined,
        aktiv: aktiv.value,
      })
    } else {
      await store.create({ name: name.value, beschreibung: beschreibung.value || undefined })
    }
    modalOffen.value = false
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}

const zuDeaktivierendeUmgebung = ref<SchemaUmgebung | null>(null)

async function bestaetigenDeaktivieren(): Promise<void> {
  if (!zuDeaktivierendeUmgebung.value) return
  await store.deactivate(zuDeaktivierendeUmgebung.value.id)
  zuDeaktivierendeUmgebung.value = null
}
</script>

<template>
  <main>
    <p class="breadcrumb">
      <RouterLink :to="{ name: 'einstellungen' }">← Zurück zu Einstellungen</RouterLink>
    </p>

    <header class="toolbar">
      <h1>Umgebungen verwalten</h1>
      <button type="button" class="button-primary" @click="neuOeffnen">Neue Umgebung anlegen</button>
    </header>
    <p class="hinweis">
      Namenskonvention für Schuldatenbanken (ADR-012). "PRODUKTIV" ist als Systemumgebung reserviert: kein Namenssuffix,
      höchstens ein aktives Schema je Schule - Name und Status sind unveränderlich.
    </p>

    <p v-if="store.errorMessage" role="alert" class="fehler">{{ store.errorMessage }}</p>
    <p v-else-if="store.loading">Lädt …</p>

    <div v-else class="table-wrap">
      <table>
        <caption class="sr-only">
          Liste der Schema-Umgebungen
        </caption>
        <thead>
          <tr>
            <th scope="col">Name</th>
            <th scope="col">Beschreibung</th>
            <th scope="col">Status</th>
            <th scope="col">Aktionen</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="umgebung in store.items" :key="umgebung.id">
            <td>
              {{ umgebung.name }}
              <span v-if="umgebung.system" class="badge">System</span>
            </td>
            <td>{{ umgebung.beschreibung ?? '–' }}</td>
            <td>
              <span :class="['status', umgebung.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                {{ umgebung.aktiv ? 'Aktiv' : 'Deaktiviert' }}
              </span>
            </td>
            <td class="aktionen-zelle">
              <button type="button" class="button-secondary btn-klein" @click="bearbeiten(umgebung)">Bearbeiten</button>
              <button
                v-if="!umgebung.system && umgebung.aktiv"
                type="button"
                class="danger btn-klein"
                @click="zuDeaktivierendeUmgebung = umgebung"
              >
                Deaktivieren
              </button>
            </td>
          </tr>
          <tr v-if="store.items.length === 0">
            <td colspan="4">Keine Umgebungen erfasst.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal
      :open="modalOffen"
      :titel="bearbeiteId ? 'Umgebung bearbeiten' : 'Neue Umgebung anlegen'"
      @close="modalSchliessen"
    >
      <form @submit.prevent="absenden">
        <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>
        <p v-if="bearbeiteIstSystem" class="hinweis-klein">
          Systemumgebungen können nicht umbenannt oder deaktiviert werden, nur die Beschreibung ist änderbar.
        </p>

        <div class="feld">
          <label for="umgebung-name">Name</label>
          <input id="umgebung-name" v-model="name" type="text" required :disabled="bearbeiteIstSystem" />
        </div>

        <div class="feld">
          <label for="umgebung-beschreibung">Beschreibung (optional)</label>
          <textarea id="umgebung-beschreibung" v-model="beschreibung" rows="2"></textarea>
        </div>

        <div v-if="bearbeiteId" class="feld feld-checkbox">
          <label for="umgebung-aktiv">
            <input id="umgebung-aktiv" v-model="aktiv" type="checkbox" :disabled="bearbeiteIstSystem" />
            Aktiv
          </label>
        </div>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="speichern">
            {{ bearbeiteId ? 'Aktualisieren' : 'Anlegen' }}
          </button>
          <button type="button" class="button-secondary" @click="modalSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="zuDeaktivierendeUmgebung !== null"
      titel="Umgebung deaktivieren"
      :nachricht="`Soll die Umgebung '${zuDeaktivierendeUmgebung?.name}' wirklich deaktiviert werden? Sie steht dann beim Anlegen neuer Schuldatenbanken nicht mehr zur Auswahl.`"
      @confirm="bestaetigenDeaktivieren"
      @cancel="zuDeaktivierendeUmgebung = null"
    />
  </main>
</template>

<style scoped>
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
  margin: 0.5rem 0 1.5rem;
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

.aktionen-zelle {
  display: flex;
  flex-wrap: nowrap;
  gap: 0.4rem;
}

.btn-klein {
  padding: 0.2rem 0.6rem;
  font-size: 0.85em;
}

.badge {
  display: inline-block;
  margin-left: 0.4rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: var(--surface-strong);
  color: var(--ink-soft);
  font-size: 0.72rem;
  font-weight: 600;
  vertical-align: middle;
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
