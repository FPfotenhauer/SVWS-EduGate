<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Modal from '@/components/Modal.vue'
import { useAnsprechpartnerStore } from '@/stores/ansprechpartnerStore'
import { useSchuldateiStore } from '@/stores/schuldateiStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { ApiError } from '@/types/problem'
import type { Ansprechpartner } from '@/types/ansprechpartner'
import type { SchultraegerKatalog } from '@/types/schuldatei'
import type { Schultraeger } from '@/types/schultraeger'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useSchultraegerStore()
const ansprechpartnerStore = useAnsprechpartnerStore()
const schuleStore = useSchuleStore()
const schuldateiStore = useSchuldateiStore()

const geladenerSchultraeger = ref<Schultraeger | null>(null)

const name = ref('')
const traegernummer = ref('')
const strasse = ref('')
const plz = ref('')
const ort = ref('')
const beschreibung = ref('')
const speichern = ref(false)
const fehler = ref<string | null>(null)

const bearbeitenModalOffen = ref(false)
const zuDeaktivierenBestaetigen = ref(false)
const zuLoeschenBestaetigen = ref(false)

const bearbeitenModus = !!props.id

// --- Übernahme aus dem Schuldatei-Katalog beim Neuanlegen (ADR-020 "Schritt 2") -----
// Sonderfälle (Schulträger ohne Katalogeintrag) bleiben möglich: Ohne Katalogauswahl kann die
// Herkunft optional als Sonderfall mit Begründung markiert werden, sonst gilt MANUELL.
const katalogAuswahlOffen = ref(false)
const katalogSuchbegriff = ref('')
const ausgewaehlterKatalogEintrag = ref<SchultraegerKatalog | null>(null)
const istSonderfall = ref(false)
const sonderfallHinweis = ref('')

async function katalogAuswahlOeffnen(): Promise<void> {
  katalogAuswahlOffen.value = true
  katalogSuchbegriff.value = ''
  await schuldateiStore.fetchSchultraeger({ page: 0, q: '', nurAktive: true })
}

async function katalogSuchen(): Promise<void> {
  await schuldateiStore.fetchSchultraeger({ page: 0, q: katalogSuchbegriff.value })
}

function katalogEintragUebernehmen(eintrag: SchultraegerKatalog): void {
  name.value = eintrag.traegername
  traegernummer.value = eintrag.traegernummer
  strasse.value = eintrag.strasse ?? ''
  plz.value = eintrag.plz ?? ''
  ort.value = eintrag.ort ?? ''
  ausgewaehlterKatalogEintrag.value = eintrag
  istSonderfall.value = false
  sonderfallHinweis.value = ''
  katalogAuswahlOffen.value = false
}

function katalogAuswahlAufheben(): void {
  ausgewaehlterKatalogEintrag.value = null
}

onMounted(async () => {
  if (props.id) {
    geladenerSchultraeger.value = await store.get(props.id)
    await ansprechpartnerStore.fetchList(props.id)
    await schuleStore.fetchList(props.id)
  }
})

function formularAusSchultraegerFuellen(schultraeger: Schultraeger): void {
  name.value = schultraeger.name
  traegernummer.value = schultraeger.traegernummer
  strasse.value = schultraeger.strasse ?? ''
  plz.value = schultraeger.plz ?? ''
  ort.value = schultraeger.ort ?? ''
  beschreibung.value = schultraeger.beschreibung ?? ''
}

function bearbeitenOeffnen(): void {
  if (geladenerSchultraeger.value) {
    formularAusSchultraegerFuellen(geladenerSchultraeger.value)
  }
  fehler.value = null
  bearbeitenModalOffen.value = true
}

function bearbeitenSchliessen(): void {
  bearbeitenModalOffen.value = false
}

async function bestaetigenDeaktivieren(): Promise<void> {
  if (!props.id) return
  await store.deactivate(props.id)
  zuDeaktivierenBestaetigen.value = false
  await router.push({ name: 'schultraeger-liste' })
}

async function reaktivieren(): Promise<void> {
  if (!props.id) return
  geladenerSchultraeger.value = await store.reactivate(props.id)
}

async function bestaetigenEndgueltigLoeschen(): Promise<void> {
  if (!props.id) return
  fehler.value = null
  zuLoeschenBestaetigen.value = false
  try {
    await store.deleteEndgueltig(props.id)
    await router.push({ name: 'schultraeger-liste' })
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Löschen fehlgeschlagen.'
  }
}

async function absenden(): Promise<void> {
  fehler.value = null
  speichern.value = true
  try {
    const basisDaten = {
      name: name.value,
      traegernummer: traegernummer.value,
      strasse: strasse.value || undefined,
      plz: plz.value || undefined,
      ort: ort.value || undefined,
      beschreibung: beschreibung.value || undefined,
    }
    if (props.id) {
      geladenerSchultraeger.value = await store.update(props.id, basisDaten)
      bearbeitenModalOffen.value = false
    } else {
      await store.create({
        ...basisDaten,
        katalogId: ausgewaehlterKatalogEintrag.value?.id,
        sonderfall: istSonderfall.value,
        sonderfallHinweis: sonderfallHinweis.value || undefined,
      })
      await router.push({ name: 'schultraeger-liste' })
    }
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}

// --- Ansprechpartner: ein Formular für Anlegen und Bearbeiten, in einem Modal -----
const apName = ref('')
const apVorname = ref('')
const apTitel = ref('')
const apAbteilung = ref('')
const apFunktion = ref('')
const apEmail = ref('')
const apTelefonFestnetz = ref('')
const apTelefonMobil = ref('')
const apBeschreibung = ref('')
const apBearbeiteId = ref<string | null>(null)
const apSpeichern = ref(false)
const apFehler = ref<string | null>(null)
const apModalOffen = ref(false)
const zuLoeschenderAnsprechpartner = ref<Ansprechpartner | null>(null)

function apFormularZuruecksetzen(): void {
  apBearbeiteId.value = null
  apName.value = ''
  apVorname.value = ''
  apTitel.value = ''
  apAbteilung.value = ''
  apFunktion.value = ''
  apEmail.value = ''
  apTelefonFestnetz.value = ''
  apTelefonMobil.value = ''
  apBeschreibung.value = ''
  apFehler.value = null
}

function apHinzufuegenOeffnen(): void {
  apFormularZuruecksetzen()
  apModalOffen.value = true
}

function apBearbeiten(ansprechpartner: Ansprechpartner): void {
  apBearbeiteId.value = ansprechpartner.id
  apName.value = ansprechpartner.name
  apVorname.value = ansprechpartner.vorname
  apTitel.value = ansprechpartner.titel ?? ''
  apAbteilung.value = ansprechpartner.abteilung ?? ''
  apFunktion.value = ansprechpartner.funktion ?? ''
  apEmail.value = ansprechpartner.email ?? ''
  apTelefonFestnetz.value = ansprechpartner.telefonFestnetz ?? ''
  apTelefonMobil.value = ansprechpartner.telefonMobil ?? ''
  apBeschreibung.value = ansprechpartner.beschreibung ?? ''
  apFehler.value = null
  apModalOffen.value = true
}

function apModalSchliessen(): void {
  apModalOffen.value = false
}

async function apAbsenden(): Promise<void> {
  if (!props.id) return
  apFehler.value = null
  apSpeichern.value = true
  try {
    const daten = {
      name: apName.value,
      vorname: apVorname.value,
      titel: apTitel.value || undefined,
      abteilung: apAbteilung.value || undefined,
      funktion: apFunktion.value || undefined,
      email: apEmail.value || undefined,
      telefonFestnetz: apTelefonFestnetz.value || undefined,
      telefonMobil: apTelefonMobil.value || undefined,
      beschreibung: apBeschreibung.value || undefined,
    }
    if (apBearbeiteId.value) {
      await ansprechpartnerStore.update(props.id, apBearbeiteId.value, daten)
    } else {
      await ansprechpartnerStore.create(props.id, daten)
    }
    apModalOffen.value = false
  } catch (error) {
    apFehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    apSpeichern.value = false
  }
}

async function bestaetigenAnsprechpartnerLoeschen(): Promise<void> {
  if (!props.id || !zuLoeschenderAnsprechpartner.value) return
  await ansprechpartnerStore.remove(props.id, zuLoeschenderAnsprechpartner.value.id)
  if (apBearbeiteId.value === zuLoeschenderAnsprechpartner.value.id) {
    apModalOffen.value = false
  }
  zuLoeschenderAnsprechpartner.value = null
}

// Schulen sind seit ADR-013 kein primär hier verstecktes CRUD mehr: Diese Ansicht zeigt nur noch
// eine kompakte, lesende Liste mit Querverweisen in die Schuldatenbank-Übersicht bzw. die
// Schule-Detailseite (dort auch Anlegen/Bearbeiten). Betreiber-Hauptsicht ist "Schuldatenbanken".
</script>

<template>
  <main>
    <h1 v-if="!bearbeitenModus">Neuen Schulträger anlegen</h1>

    <template v-if="bearbeitenModus">
      <p v-if="fehler && !bearbeitenModalOffen" role="alert" class="fehler">{{ fehler }}</p>

      <header class="toolbar">
        <h1>{{ geladenerSchultraeger?.name ?? 'Schulträger' }}</h1>
        <button type="button" class="button-secondary btn-klein" @click="bearbeitenOeffnen">Bearbeiten</button>
      </header>

      <dl v-if="geladenerSchultraeger" class="detail-grid">
        <div class="detail-eintrag">
          <dt>Trägernummer</dt>
          <dd>{{ geladenerSchultraeger.traegernummer }}</dd>
        </div>
        <div class="detail-eintrag">
          <dt>Status</dt>
          <dd>
            <span :class="['status', geladenerSchultraeger.aktiv ? 'status-aktiv' : 'status-inaktiv']">
              {{ geladenerSchultraeger.aktiv ? 'Aktiv' : 'Deaktiviert' }}
            </span>
          </dd>
        </div>
        <div class="detail-eintrag">
          <dt>Straße</dt>
          <dd>{{ geladenerSchultraeger.strasse ?? '–' }}</dd>
        </div>
        <div class="detail-eintrag">
          <dt>PLZ</dt>
          <dd>{{ geladenerSchultraeger.plz ?? '–' }}</dd>
        </div>
        <div class="detail-eintrag">
          <dt>Ort</dt>
          <dd>{{ geladenerSchultraeger.ort ?? '–' }}</dd>
        </div>
        <div class="detail-eintrag detail-eintrag-breit">
          <dt>Beschreibung</dt>
          <dd>{{ geladenerSchultraeger.beschreibung ?? '–' }}</dd>
        </div>
      </dl>

      <section v-if="geladenerSchultraeger" class="operationen">
        <h2>Operationen</h2>
        <div class="operationen-grid">
          <article v-if="geladenerSchultraeger.aktiv" class="operation-karte gefahr">
            <h3>Schulträger deaktivieren</h3>
            <p>Ein deaktivierter Schulträger bleibt erhalten, ist aber nicht mehr aktiv nutzbar.</p>
            <button type="button" class="danger" @click="zuDeaktivierenBestaetigen = true">Deaktivieren</button>
          </article>

          <article v-else class="operation-karte">
            <h3>Schulträger reaktivieren</h3>
            <p>Der Schulträger wird wieder aktiv nutzbar.</p>
            <button type="button" class="button-primary" @click="reaktivieren">Reaktivieren</button>
          </article>

          <article class="operation-karte gefahr">
            <h3>Schulträger endgültig löschen</h3>
            <p>
              Nur möglich für manuell angelegte Schulträger ohne Schulen (kein Landeslisten-Bezug). Diese Aktion kann
              nicht rückgängig gemacht werden.
            </p>
            <button type="button" class="danger" @click="zuLoeschenBestaetigen = true">Endgültig löschen</button>
          </article>
        </div>
      </section>
    </template>

    <form v-else @submit.prevent="absenden">
      <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

      <div class="katalog-bereich">
        <div v-if="ausgewaehlterKatalogEintrag" class="katalog-badge">
          <span class="status status-aktiv">
            Aus der Schuldatei übernommen: {{ ausgewaehlterKatalogEintrag.traegernummer }}
          </span>
          <button type="button" class="button-secondary btn-klein" @click="katalogAuswahlAufheben">
            Auswahl aufheben
          </button>
        </div>
        <button v-else type="button" class="button-secondary" @click="katalogAuswahlOeffnen">
          Aus Schuldatei übernehmen …
        </button>
      </div>

      <div v-if="!ausgewaehlterKatalogEintrag" class="feld feld-checkbox">
        <label>
          <input v-model="istSonderfall" type="checkbox" />
          Sonderfall (kein Eintrag in der Schuldatei)
        </label>
      </div>

      <div v-if="!ausgewaehlterKatalogEintrag && istSonderfall" class="feld">
        <label for="sonderfall-hinweis">Begründung</label>
        <textarea id="sonderfall-hinweis" v-model="sonderfallHinweis" rows="2"></textarea>
      </div>

      <div class="feld">
        <label for="name">Name</label>
        <input id="name" v-model="name" type="text" required />
      </div>

      <div class="feld">
        <label for="traegernummer">Trägernummer</label>
        <input id="traegernummer" v-model="traegernummer" type="text" required />
      </div>

      <div class="feld">
        <label for="strasse">Straße</label>
        <input id="strasse" v-model="strasse" type="text" />
      </div>

      <div class="feld feld-kompakt">
        <label for="plz">PLZ</label>
        <input id="plz" v-model="plz" type="text" />
      </div>

      <div class="feld">
        <label for="ort">Ort</label>
        <input id="ort" v-model="ort" type="text" />
      </div>

      <div class="feld">
        <label for="beschreibung">Beschreibung (optional)</label>
        <textarea id="beschreibung" v-model="beschreibung" rows="3"></textarea>
      </div>

      <div class="aktionen">
        <button type="submit" class="button-primary" :disabled="speichern">Speichern</button>
        <RouterLink :to="{ name: 'schultraeger-liste' }" class="button-secondary">Abbrechen</RouterLink>
      </div>
    </form>

    <section v-if="bearbeitenModus" class="ansprechpartner">
      <h2>Ansprechpartner</h2>

      <p v-if="ansprechpartnerStore.errorMessage" role="alert" class="fehler">
        {{ ansprechpartnerStore.errorMessage }}
      </p>
      <p v-else-if="ansprechpartnerStore.loading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Liste der Ansprechpartner
          </caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Vorname</th>
              <th scope="col">Titel</th>
              <th scope="col">Abteilung</th>
              <th scope="col">Funktion</th>
              <th scope="col">E-Mail</th>
              <th scope="col">Telefon</th>
              <th scope="col">Beschreibung</th>
              <th scope="col">Aktionen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ansprechpartner in ansprechpartnerStore.items" :key="ansprechpartner.id">
              <td>{{ ansprechpartner.name }}</td>
              <td>{{ ansprechpartner.vorname }}</td>
              <td>{{ ansprechpartner.titel ?? '–' }}</td>
              <td>{{ ansprechpartner.abteilung ?? '–' }}</td>
              <td>{{ ansprechpartner.funktion ?? '–' }}</td>
              <td>{{ ansprechpartner.email ?? '–' }}</td>
              <td>
                <span v-if="ansprechpartner.telefonFestnetz">F: {{ ansprechpartner.telefonFestnetz }}</span>
                <span v-if="ansprechpartner.telefonFestnetz && ansprechpartner.telefonMobil"><br /></span>
                <span v-if="ansprechpartner.telefonMobil">M: {{ ansprechpartner.telefonMobil }}</span>
                <span v-if="!ansprechpartner.telefonFestnetz && !ansprechpartner.telefonMobil">–</span>
              </td>
              <td class="beschreibung-zelle" :title="ansprechpartner.beschreibung ?? ''">
                {{ ansprechpartner.beschreibung ?? '–' }}
              </td>
              <td class="aktionen-zelle">
                <button type="button" class="button-secondary btn-klein" @click="apBearbeiten(ansprechpartner)">
                  Bearbeiten
                </button>
                <button type="button" class="danger btn-klein" @click="zuLoeschenderAnsprechpartner = ansprechpartner">
                  Löschen
                </button>
              </td>
            </tr>
            <tr v-if="ansprechpartnerStore.items.length === 0">
              <td colspan="9">Keine Ansprechpartner erfasst.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="aktionen">
        <button type="button" class="button-primary" @click="apHinzufuegenOeffnen">Hinzufügen</button>
      </div>
    </section>

    <section v-if="bearbeitenModus" class="schulen">
      <h2>Schulen</h2>

      <p v-if="schuleStore.errorMessage" role="alert" class="fehler">
        {{ schuleStore.errorMessage }}
      </p>
      <p v-else-if="schuleStore.loading">Lädt …</p>

      <div v-else class="table-wrap">
        <table>
          <caption class="sr-only">
            Liste der Schulen
          </caption>
          <thead>
            <tr>
              <th scope="col">Schulnummer</th>
              <th scope="col">Name</th>
              <th scope="col">Status</th>
              <th scope="col">Aktionen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="schule in schuleStore.items" :key="schule.id">
              <td>{{ schule.schulnummer }}</td>
              <td>{{ schule.name }}</td>
              <td>
                <span :class="['status', schule.aktiv ? 'status-aktiv' : 'status-inaktiv']">
                  {{ schule.aktiv ? 'Aktiv' : 'Deaktiviert' }}
                </span>
              </td>
              <td class="aktionen-zelle">
                <RouterLink
                  :to="{ name: 'schule-detail', params: { schultraegerId: props.id, schuleId: schule.id } }"
                  class="button-secondary btn-klein"
                >
                  Zur Schule
                </RouterLink>
              </td>
            </tr>
            <tr v-if="schuleStore.items.length === 0">
              <td colspan="4">Keine Schulen erfasst.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <Modal
      :open="katalogAuswahlOffen"
      titel="Schulträger aus der Schuldatei übernehmen"
      @close="katalogAuswahlOffen = false"
    >
      <form class="katalog-suche" @submit.prevent="katalogSuchen">
        <div class="feld">
          <label for="katalog-suche">Suche nach Name, Trägernummer oder Ort</label>
          <input id="katalog-suche" v-model="katalogSuchbegriff" type="search" />
        </div>
        <button type="submit" class="button-secondary">Suchen</button>
      </form>

      <p v-if="schuldateiStore.schultraegerError" role="alert" class="fehler">
        {{ schuldateiStore.schultraegerError }}
      </p>
      <p v-else-if="schuldateiStore.schultraegerLoading">Lädt …</p>
      <ul v-else class="katalog-liste">
        <li v-for="eintrag in schuldateiStore.schultraeger" :key="eintrag.id">
          <div>
            <strong>{{ eintrag.traegername }}</strong>
            <span class="katalog-hinweis">{{ eintrag.traegernummer }} · {{ eintrag.ort ?? '–' }}</span>
          </div>
          <button type="button" class="button-secondary btn-klein" @click="katalogEintragUebernehmen(eintrag)">
            Übernehmen
          </button>
        </li>
        <li v-if="schuldateiStore.schultraeger.length === 0" class="katalog-hinweis">Keine Treffer.</li>
      </ul>
    </Modal>

    <Modal :open="bearbeitenModalOffen" titel="Schulträger bearbeiten" @close="bearbeitenSchliessen">
      <form @submit.prevent="absenden">
        <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

        <div class="feld">
          <label for="modal-name">Name</label>
          <input id="modal-name" v-model="name" type="text" required />
        </div>

        <div class="feld">
          <label for="modal-traegernummer">Trägernummer</label>
          <input id="modal-traegernummer" v-model="traegernummer" type="text" required />
        </div>

        <div class="feld">
          <label for="modal-strasse">Straße</label>
          <input id="modal-strasse" v-model="strasse" type="text" />
        </div>

        <div class="feld feld-kompakt">
          <label for="modal-plz">PLZ</label>
          <input id="modal-plz" v-model="plz" type="text" />
        </div>

        <div class="feld">
          <label for="modal-ort">Ort</label>
          <input id="modal-ort" v-model="ort" type="text" />
        </div>

        <div class="feld">
          <label for="modal-beschreibung">Beschreibung (optional)</label>
          <textarea id="modal-beschreibung" v-model="beschreibung" rows="3"></textarea>
        </div>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="speichern">Speichern</button>
          <button type="button" class="button-secondary" @click="bearbeitenSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <Modal
      :open="apModalOffen"
      :titel="apBearbeiteId ? 'Ansprechpartner bearbeiten' : 'Neuen Ansprechpartner hinzufügen'"
      @close="apModalSchliessen"
    >
      <form @submit.prevent="apAbsenden">
        <p v-if="apFehler" role="alert" class="fehler">{{ apFehler }}</p>

        <div class="ap-felder">
          <div class="feld">
            <label for="ap-name">Name</label>
            <input id="ap-name" v-model="apName" type="text" required />
          </div>
          <div class="feld">
            <label for="ap-vorname">Vorname</label>
            <input id="ap-vorname" v-model="apVorname" type="text" required />
          </div>
          <div class="feld">
            <label for="ap-titel">Titel</label>
            <input id="ap-titel" v-model="apTitel" type="text" />
          </div>
          <div class="feld">
            <label for="ap-abteilung">Abteilung</label>
            <input id="ap-abteilung" v-model="apAbteilung" type="text" />
          </div>
          <div class="feld">
            <label for="ap-funktion">Funktion</label>
            <input id="ap-funktion" v-model="apFunktion" type="text" />
          </div>
          <div class="feld">
            <label for="ap-email">E-Mail</label>
            <input id="ap-email" v-model="apEmail" type="email" />
          </div>
          <div class="feld">
            <label for="ap-telefon-festnetz">Telefon (Festnetz)</label>
            <input id="ap-telefon-festnetz" v-model="apTelefonFestnetz" type="text" />
          </div>
          <div class="feld">
            <label for="ap-telefon-mobil">Telefon (Mobil)</label>
            <input id="ap-telefon-mobil" v-model="apTelefonMobil" type="text" />
          </div>
        </div>

        <div class="feld">
          <label for="ap-beschreibung">Beschreibung (optional)</label>
          <textarea id="ap-beschreibung" v-model="apBeschreibung" rows="2"></textarea>
        </div>

        <div class="aktionen">
          <button type="submit" class="button-primary" :disabled="apSpeichern">
            {{ apBearbeiteId ? 'Aktualisieren' : 'Hinzufügen' }}
          </button>
          <button type="button" class="button-secondary" @click="apModalSchliessen">Abbrechen</button>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      :open="zuDeaktivierenBestaetigen"
      titel="Schulträger deaktivieren"
      :nachricht="`Soll '${geladenerSchultraeger?.name}' wirklich deaktiviert werden?`"
      @confirm="bestaetigenDeaktivieren"
      @cancel="zuDeaktivierenBestaetigen = false"
    />

    <ConfirmDialog
      :open="zuLoeschenBestaetigen"
      titel="Schulträger endgültig löschen"
      :nachricht="`Soll '${geladenerSchultraeger?.name}' unwiderruflich gelöscht werden? Diese Aktion kann nicht rückgängig gemacht werden.`"
      bestaetigen-text="Endgültig löschen"
      @confirm="bestaetigenEndgueltigLoeschen"
      @cancel="zuLoeschenBestaetigen = false"
    />

    <ConfirmDialog
      :open="zuLoeschenderAnsprechpartner !== null"
      titel="Ansprechpartner löschen"
      :nachricht="`Soll '${zuLoeschenderAnsprechpartner?.vorname} ${zuLoeschenderAnsprechpartner?.name}' wirklich gelöscht werden?`"
      bestaetigen-text="Löschen"
      @confirm="bestaetigenAnsprechpartnerLoeschen"
      @cancel="zuLoeschenderAnsprechpartner = null"
    />
  </main>
</template>

<style scoped>
/* Die Ansprechpartner-Tabelle hat viele Spalten und braucht daher mehr Breite als
   der globale main-Rahmen (60rem, style.css) vorgibt. */
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

.feld-kompakt {
  max-width: 10rem;
}

.feld-checkbox label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: normal;
}

.katalog-bereich {
  margin-bottom: 1.25rem;
}

.katalog-badge {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

.katalog-suche {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.katalog-suche .feld {
  flex: 1 1 auto;
  margin-bottom: 0;
  max-width: none;
}

.katalog-liste {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 22rem;
  overflow-y: auto;
}

.katalog-liste li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.katalog-hinweis {
  display: block;
  color: var(--ink-soft);
  font-size: 0.85rem;
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.5rem;
}

.operationen,
.ansprechpartner,
.schulen {
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

.ansprechpartner .aktionen,
.schulen .aktionen {
  margin-top: 1rem;
}

.ap-felder {
  display: flex;
  flex-wrap: wrap;
  gap: 0 1.5rem;
}

.ap-felder .feld {
  max-width: 16rem;
  flex: 1 1 14rem;
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

.beschreibung-zelle {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status-inaktiv {
  color: var(--ink-soft);
}

.hinweis {
  color: var(--ink-soft);
  max-width: 36rem;
}

.hinweis a {
  color: var(--accent);
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
