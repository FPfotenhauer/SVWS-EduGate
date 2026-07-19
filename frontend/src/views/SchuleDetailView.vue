<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useSchemaStore } from '@/stores/schemaStore'
import { useSchemaUmgebungStore } from '@/stores/schemaUmgebungStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { Schule } from '@/types/schule'
import type { Schultraeger } from '@/types/schultraeger'

// Betreiber-Arbeitsseite für eine Schule (statt einer Tabellen-Ansicht, die die Haupt-Übersicht
// "Schuldatenbanken" nur dupliziert hätte): zeigt, welche Operationen an dieser Schule möglich
// sind. Migration/Backup existieren backend-seitig noch nicht (ADR-012/013 stellen das bewusst
// zurück) und erscheinen deshalb sichtbar, aber deaktiviert - keine vorgetäuschte Funktionalität.

const props = defineProps<{ schultraegerId: string; schuleId: string }>()

const route = useRoute()
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
  await Promise.all([svwsInstanzStore.fetchList({ page: 0 }), schemaUmgebungStore.fetchList()])

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

// --- Operation "Schule deaktivieren" --------------------------------------------------------
const deaktivierenBestaetigen = ref(false)

async function bestaetigenSchuleDeaktivieren(): Promise<void> {
  geladeneSchule.value = await schuleStore.deactivate(props.schultraegerId, props.schuleId)
  deaktivierenBestaetigen.value = false
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
      </div>

      <p class="hinweis-klein">Weitere Operationen (z. B. Zertifikatsverwaltung, Credential-Rotation) folgen später.</p>
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
</style>
