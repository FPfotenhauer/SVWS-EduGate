<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Modal from '@/components/Modal.vue'
import { useSchemaUmgebungStore } from '@/stores/schemaUmgebungStore'
import { useSchuleStore } from '@/stores/schuleStore'
import { useSchultraegerStore } from '@/stores/schultraegerStore'
import { useSvwsSchemaFundStore } from '@/stores/svwsSchemaFundStore'
import { ApiError } from '@/types/problem'
import type { SvwsSchemaFund } from '@/types/svwsSchemaFund'

/**
 * Kontrollierter Zuordnungs-Workflow für einen unzugeordneten SVWS-Schema-Fund (ADR-014 Schritt 2,
 * ADR-012 "Unzugeordnete und importierte Schemata"): Schulträger, Schule und Umgebung müssen
 * bewusst ausgewählt werden - SVWS-Schulinformationen dienen nur als unverbindliche Orientierung
 * und setzen nie automatisch Tenant/Schule.
 */
const props = defineProps<{
  open: boolean
  instanzId: string
  instanzName: string
  fund: SvwsSchemaFund | null
}>()

const emit = defineEmits<{ close: []; zugeordnet: [] }>()

const schemaFundStore = useSvwsSchemaFundStore()
const schultraegerStore = useSchultraegerStore()
const schuleStore = useSchuleStore()
const schemaUmgebungStore = useSchemaUmgebungStore()

const schultraegerId = ref('')
const schuleId = ref('')
const umgebung = ref('')
const beschreibung = ref('')
const speichern = ref(false)
const speichernFehler = ref<string | null>(null)

const aktiveSchultraeger = computed(() => schultraegerStore.items.filter((s) => s.aktiv))
const aktiveSchulen = computed(() => schuleStore.items.filter((s) => s.aktiv))
const aktiveUmgebungen = computed(() => schemaUmgebungStore.items.filter((u) => u.aktiv))

const schulInfoErgebnis = computed(() => (props.fund ? schemaFundStore.schulInfoByFund[props.fund.id] : undefined))
const schulInfoLaedt = computed(() => (props.fund ? schemaFundStore.schulInfoLoadingByFund[props.fund.id] : false))

watch(
  () => [props.open, props.fund?.id],
  async ([isOpen]) => {
    if (!isOpen || !props.fund) {
      return
    }
    schultraegerId.value = ''
    schuleId.value = ''
    umgebung.value = ''
    beschreibung.value = ''
    speichernFehler.value = null
    schultraegerStore.size = 200
    await Promise.all([
      schultraegerStore.fetchList({ page: 0 }),
      schemaUmgebungStore.fetchList(),
      schemaFundStore.fetchSchulInfo(props.instanzId, props.fund.id),
    ])
  },
)

watch(schultraegerId, async (neueSchultraegerId) => {
  schuleId.value = ''
  if (neueSchultraegerId) {
    await schuleStore.fetchList(neueSchultraegerId)
  }
})

function formatiereFlag(value: boolean | null): string {
  if (value === null) return '–'
  return value ? 'Ja' : 'Nein'
}

function formatiereZeitpunkt(iso: string | null | undefined): string {
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

function schliessen(): void {
  emit('close')
}

async function zuordnungAbsenden(): Promise<void> {
  if (!props.fund || !schultraegerId.value || !schuleId.value || !umgebung.value) {
    return
  }
  speichernFehler.value = null
  speichern.value = true
  try {
    await schemaFundStore.assign(props.instanzId, props.fund.id, {
      schultraegerId: schultraegerId.value,
      schuleId: schuleId.value,
      umgebung: umgebung.value,
      beschreibung: beschreibung.value || undefined,
    })
    emit('zugeordnet')
    emit('close')
  } catch (error) {
    speichernFehler.value = error instanceof ApiError ? error.message : 'Zuordnung fehlgeschlagen.'
  } finally {
    speichern.value = false
  }
}
</script>

<template>
  <Modal :open="open" titel="SVWS-Schema zuordnen" @close="schliessen">
    <div v-if="fund" class="zuordnung-modal">
      <section class="fund-details">
        <h3>Technischer Fund</h3>
        <dl>
          <div>
            <dt>SVWS-Instanz</dt>
            <dd>{{ instanzName }}</dd>
          </div>
          <div>
            <dt>Schemaname</dt>
            <dd>{{ fund.schemaName }}</dd>
          </div>
          <div>
            <dt>Benutzername</dt>
            <dd>{{ fund.username }}</dd>
          </div>
          <div>
            <dt>Revision</dt>
            <dd>{{ fund.revision ?? '–' }}</dd>
          </div>
          <div>
            <dt>SVWS-Schema</dt>
            <dd>{{ formatiereFlag(fund.isSvws) }}</dd>
          </div>
          <div>
            <dt>In Config</dt>
            <dd>{{ formatiereFlag(fund.isInConfig) }}</dd>
          </div>
          <div>
            <dt>Deaktiviert</dt>
            <dd>{{ formatiereFlag(fund.isDeactivated) }}</dd>
          </div>
          <div>
            <dt>Tainted</dt>
            <dd>{{ formatiereFlag(fund.isTainted) }}</dd>
          </div>
          <div>
            <dt>Zuletzt gesehen</dt>
            <dd>{{ formatiereZeitpunkt(fund.lastSeenAt) }}</dd>
          </div>
        </dl>
      </section>

      <section class="schulinfo">
        <h3>SVWS-Schulinformationen <span class="hinweis-klein">(Orientierung, keine automatische Übernahme)</span></h3>
        <p v-if="schulInfoLaedt" class="hinweis-klein">Lädt …</p>
        <p v-else-if="!schulInfoErgebnis || !schulInfoErgebnis.success" class="hinweis-klein">
          Keine SVWS-Schulinformationen verfügbar{{ schulInfoErgebnis ? ': ' + schulInfoErgebnis.message : '' }}. Die
          Zuordnung bleibt manuell möglich.
        </p>
        <dl v-else-if="schulInfoErgebnis.schulInfo">
          <div>
            <dt>Schule</dt>
            <dd>{{ schulInfoErgebnis.schulInfo.bezeichnung ?? '–' }}</dd>
          </div>
          <div>
            <dt>Schulnummer (SVWS)</dt>
            <dd>{{ schulInfoErgebnis.schulInfo.schulnummer ?? '–' }}</dd>
          </div>
          <div>
            <dt>Schulform</dt>
            <dd>{{ schulInfoErgebnis.schulInfo.schulform ?? '–' }}</dd>
          </div>
          <div>
            <dt>Adresse</dt>
            <dd>
              {{
                [schulInfoErgebnis.schulInfo.strassenname, schulInfoErgebnis.schulInfo.hausnummer]
                  .filter(Boolean)
                  .join(' ') || '–'
              }},
              {{ [schulInfoErgebnis.schulInfo.plz, schulInfoErgebnis.schulInfo.ort].filter(Boolean).join(' ') || '–' }}
            </dd>
          </div>
        </dl>
      </section>

      <form class="zuordnung-form" @submit.prevent="zuordnungAbsenden">
        <p v-if="speichernFehler" role="alert" class="fehler">{{ speichernFehler }}</p>

        <div class="feld">
          <label for="zuordnung-traeger">Schulträger</label>
          <select id="zuordnung-traeger" v-model="schultraegerId" required>
            <option value="" disabled>Bitte wählen</option>
            <option v-for="schultraeger in aktiveSchultraeger" :key="schultraeger.id" :value="schultraeger.id">
              {{ schultraeger.name }}
            </option>
          </select>
        </div>

        <div class="feld">
          <label for="zuordnung-schule">Schule</label>
          <select id="zuordnung-schule" v-model="schuleId" required :disabled="!schultraegerId">
            <option value="" disabled>Bitte wählen</option>
            <option v-for="schule in aktiveSchulen" :key="schule.id" :value="schule.id">
              {{ schule.schulnummer }} – {{ schule.name }}
            </option>
          </select>
          <p v-if="schultraegerId && aktiveSchulen.length === 0" class="hinweis-klein">
            Dieser Schulträger hat keine aktiven Schulen.
          </p>
        </div>

        <div class="feld">
          <label for="zuordnung-umgebung">Umgebung</label>
          <select id="zuordnung-umgebung" v-model="umgebung" required>
            <option value="" disabled>Bitte wählen</option>
            <option v-for="u in aktiveUmgebungen" :key="u.id" :value="u.name">{{ u.name }}</option>
          </select>
        </div>

        <div class="feld">
          <label for="zuordnung-beschreibung">Beschreibung (optional)</label>
          <textarea id="zuordnung-beschreibung" v-model="beschreibung" rows="2"></textarea>
        </div>

        <div class="aktionen">
          <button
            type="submit"
            class="button-primary"
            :disabled="speichern || !schultraegerId || !schuleId || !umgebung"
          >
            {{ speichern ? 'Wird zugeordnet …' : 'Zuordnen' }}
          </button>
          <button type="button" class="button-secondary" @click="schliessen">Abbrechen</button>
        </div>
      </form>
    </div>
  </Modal>
</template>

<style scoped>
.zuordnung-modal {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.fund-details h3,
.schulinfo h3 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.3rem 0.75rem;
  margin: 0;
}

dl div {
  display: contents;
}

dt {
  color: var(--ink-soft);
  font-size: 0.85rem;
}

dd {
  margin: 0;
  font-size: 0.9rem;
}

.hinweis-klein {
  font-size: 0.85em;
  color: var(--ink-soft);
}

.zuordnung-form {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  border-top: 1px solid var(--line);
  padding-top: 1rem;
}

.feld {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.85rem;
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
  cursor: pointer;
}

.button-secondary:hover {
  border-color: var(--accent);
}

.fehler {
  color: var(--error);
}
</style>
