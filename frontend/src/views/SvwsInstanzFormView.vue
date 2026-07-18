<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSvwsInstanzStore } from '@/stores/svwsInstanzStore'
import { ApiError } from '@/types/problem'
import type { InstanzStatus } from '@/types/svwsInstanz'

const props = defineProps<{ id?: string }>()

const router = useRouter()
const store = useSvwsInstanzStore()

const name = ref('')
const baseUrl = ref('')
const status = ref<InstanzStatus>('OK')
const aktiv = ref(true)
const speichern = ref(false)
const fehler = ref<string | null>(null)

const credentialsUsername = ref('')
const credentialsPassword = ref('')
const credentialsSpeichern = ref(false)
const credentialsFehler = ref<string | null>(null)
const credentialsErfolg = ref(false)

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
    status.value = bestehender.status
    aktiv.value = bestehender.aktiv
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
        status: status.value,
        aktiv: aktiv.value,
      })
    } else {
      await store.create({ name: name.value, baseUrl: baseUrl.value })
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
  } catch (error) {
    credentialsFehler.value =
      error instanceof ApiError ? error.message : 'Zugangsdaten konnten nicht gespeichert werden.'
  } finally {
    credentialsSpeichern.value = false
  }
}
</script>

<template>
  <main>
    <h1>{{ bearbeitenModus ? 'SVWS-Instanz bearbeiten' : 'Neue SVWS-Instanz anlegen' }}</h1>

    <form @submit.prevent="absenden">
      <p v-if="fehler" role="alert" class="fehler">{{ fehler }}</p>

      <div class="feld">
        <label for="name">Name</label>
        <input id="name" v-model="name" type="text" required />
      </div>

      <div class="feld">
        <label for="baseUrl">Base-URL</label>
        <input id="baseUrl" v-model="baseUrl" type="text" placeholder="https://svws.example.org" required />
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

.zugangsdaten {
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--line);
}

.hinweis {
  color: var(--ink-soft);
  max-width: 36rem;
}
</style>
