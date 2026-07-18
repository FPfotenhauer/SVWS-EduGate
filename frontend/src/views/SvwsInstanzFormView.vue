<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { InstanzStatus } from '@/types/svwsInstanz'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useSvwsInstanzStore()

const name = ref('')
const baseUrl = ref('')
const beschreibung = ref('')
const status = ref<InstanzStatus>('OK')
const aktiv = ref(true)
const speichern = ref(false)
const fehler = ref<string | null>(null)

const credentialsHinterlegt = ref(false)
const credentialsUpdatedAt = ref<string | null>(null)
const credentialsUsername = ref('')
const credentialsPassword = ref('')
const credentialsSpeichern = ref(false)
const credentialsFehler = ref<string | null>(null)
const credentialsErfolg = ref(false)

const lastConnectionTestAt = ref<string | null>(null)
const lastConnectionTestSuccess = ref<boolean | null>(null)
const lastConnectionTestMessage = ref<string | null>(null)
const verbindungstestLaeuft = ref(false)
const verbindungstestFehler = ref<string | null>(null)

const zuDeaktivierenBestaetigen = ref(false)

const bearbeitenModus = !!props.id

const statusOptionen: { value: InstanzStatus; label: string }[] = [
  { value: 'OK', label: 'OK' },
  { value: 'DEGRADED', label: 'Beeinträchtigt' },
  { value: 'UNREACHABLE', label: 'Nicht erreichbar' },
]

onMounted(async () => {
  if (props.id) {
    const bestehender = await store.get(props.id)
    name.value = bestehender.name
    baseUrl.value = bestehender.baseUrl
    beschreibung.value = bestehender.beschreibung ?? ''
    status.value = bestehender.status
    aktiv.value = bestehender.aktiv
    credentialsHinterlegt.value = bestehender.credentialsHinterlegt
    credentialsUpdatedAt.value = bestehender.credentialsUpdatedAt
    lastConnectionTestAt.value = bestehender.lastConnectionTestAt
    lastConnectionTestSuccess.value = bestehender.lastConnectionTestSuccess
    lastConnectionTestMessage.value = bestehender.lastConnectionTestMessage
  }
})

async function absenden(): Promise<void> {
  fehler.value = null
  speichern.value = true
  try {
    if (props.id) {
      await store.update(props.id, {
        name: name.value,
        baseUrl: baseUrl.value,
        beschreibung: beschreibung.value || undefined,
        status: status.value,
        aktiv: aktiv.value,
      })
    } else {
      await store.create({ name: name.value, baseUrl: baseUrl.value, beschreibung: beschreibung.value || undefined })
    }
    await router.push({ name: 'svws-instanz-liste' })
  } catch (error) {
    fehler.value = error instanceof ApiError ? error.message : 'Speichern fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}

async function credentialsAbsenden(): Promise<void> {
  if (!props.id) return
  credentialsFehler.value = null
  credentialsErfolg.value = false
  credentialsSpeichern.value = true
  try {
    await store.setCredentials(props.id, { username: credentialsUsername.value, password: credentialsPassword.value })
    credentialsUsername.value = ''
    credentialsPassword.value = ''
    credentialsErfolg.value = true
    const aktualisiert = await store.get(props.id)
    credentialsHinterlegt.value = aktualisiert.credentialsHinterlegt
    credentialsUpdatedAt.value = aktualisiert.credentialsUpdatedAt
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
    const aktualisiert = await store.testConnection(props.id)
    status.value = aktualisiert.status
    lastConnectionTestAt.value = aktualisiert.lastConnectionTestAt
    lastConnectionTestSuccess.value = aktualisiert.lastConnectionTestSuccess
    lastConnectionTestMessage.value = aktualisiert.lastConnectionTestMessage
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
    <h1>{{ bearbeitenModus ? 'SVWS-Instanz bearbeiten' : 'Neue SVWS-Instanz anlegen' }}</h1>

    <form @submit.prevent="absenden">
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

      <div v-if="bearbeitenModus" class="feld">
        <label for="status">Status</label>
        <select id="status" v-model="status">
          <option v-for="option in statusOptionen" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </div>

      <div v-if="bearbeitenModus" class="feld feld-checkbox">
        <label for="aktiv">
          <input id="aktiv" v-model="aktiv" type="checkbox" />
          Aktiv
        </label>
      </div>

      <div class="aktionen">
        <button type="submit" class="button-primary" :disabled="speichern">Speichern</button>
        <RouterLink :to="{ name: 'svws-instanz-liste' }">Abbrechen</RouterLink>
      </div>
    </form>

    <section v-if="bearbeitenModus" class="zugangsdaten">
      <h2>Zugangsdaten</h2>
      <p class="hinweis">
        Gespeicherte Zugangsdaten werden aus Sicherheitsgründen nie angezeigt. Hier eingegebene Werte ersetzen die
        bisherigen Zugangsdaten vollständig.
      </p>
      <p class="hinweis-status">
        Status:
        <strong>{{ credentialsHinterlegt ? 'Hinterlegt' : 'Nicht hinterlegt' }}</strong>
        <span v-if="credentialsHinterlegt"> – zuletzt geändert: {{ formatiereZeitpunkt(credentialsUpdatedAt) }}</span>
      </p>

      <form @submit.prevent="credentialsAbsenden">
        <p v-if="credentialsFehler" role="alert" class="fehler">{{ credentialsFehler }}</p>
        <p v-if="credentialsErfolg" role="status" class="erfolg">Zugangsdaten wurden gespeichert.</p>

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
          <button type="submit" class="button-primary" :disabled="credentialsSpeichern">Zugangsdaten speichern</button>
        </div>
      </form>
    </section>

    <section v-if="bearbeitenModus" class="verbindungstest">
      <h2>Verbindungstest</h2>
      <p class="hinweis">Prüft die Erreichbarkeit der Base-URL mit den hinterlegten Zugangsdaten.</p>

      <p v-if="verbindungstestFehler" role="alert" class="fehler">{{ verbindungstestFehler }}</p>
      <p v-else-if="lastConnectionTestAt" role="status" :class="lastConnectionTestSuccess ? 'erfolg' : 'fehler'">
        {{ lastConnectionTestSuccess ? 'Erfolgreich' : 'Fehlgeschlagen' }} am
        {{ formatiereZeitpunkt(lastConnectionTestAt) }}
        <span v-if="lastConnectionTestMessage">– {{ lastConnectionTestMessage }}</span>
      </p>
      <p v-else class="hinweis">Noch kein Verbindungstest durchgeführt.</p>

      <p v-if="!credentialsHinterlegt" class="hinweis">
        Keine Zugangsdaten hinterlegt – der Test prüft in diesem Fall nur die Basis-Erreichbarkeit, nicht die Gültigkeit
        von Zugangsdaten.
      </p>

      <button type="button" class="button-primary" :disabled="verbindungstestLaeuft" @click="verbindungstestStarten">
        {{ verbindungstestLaeuft ? 'Teste …' : 'Verbindung testen' }}
      </button>
    </section>

    <section v-if="bearbeitenModus && aktiv" class="gefahrenzone">
      <h2>Instanz deaktivieren</h2>
      <p class="hinweis">
        Eine deaktivierte Instanz bleibt erhalten und kann über das Feld „Aktiv" oben wieder reaktiviert werden.
      </p>
      <button type="button" class="danger" @click="zuDeaktivierenBestaetigen = true">Deaktivieren</button>
    </section>

    <ConfirmDialog
      :open="zuDeaktivierenBestaetigen"
      titel="SVWS-Instanz deaktivieren"
      :nachricht="`Soll '${name}' wirklich deaktiviert werden?`"
      @confirm="bestaetigenDeaktivieren"
      @cancel="zuDeaktivierenBestaetigen = false"
    />
  </main>
</template>

<style scoped>
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

.fehler {
  color: var(--error);
}

.erfolg {
  color: var(--accent);
}

.zugangsdaten,
.verbindungstest,
.gefahrenzone {
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--line);
}

.hinweis,
.hinweis-status {
  color: var(--ink-soft);
  max-width: 36rem;
}
</style>
