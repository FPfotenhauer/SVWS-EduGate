import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as svwsSchemaFundApi from '@/api/svwsSchemaFundApi'
import { ApiError } from '@/types/problem'
import type {
  SchemaFundZuordnungFormData,
  SvwsSchemaFund,
  SvwsSchemaSyncResult,
  SvwsSchulInfoResult,
} from '@/types/svwsSchemaFund'

/**
 * Verwaltet die SVWS-Sync-Funde je Instanz (ADR-014 Stufe 1). Nach Instanz-ID indiziert, weil die
 * Instanzliste mehrere Instanzen gleichzeitig anzeigt und je Instanz unabhängig aufklappen/
 * synchronisieren können muss (ADR-013: aufklappbare Bereiche statt Megatabelle).
 */
export const useSvwsSchemaFundStore = defineStore('svwsSchemaFund', () => {
  const itemsByInstanz = ref<Record<string, SvwsSchemaFund[]>>({})
  const loadingByInstanz = ref<Record<string, boolean>>({})
  const errorByInstanz = ref<Record<string, string | null>>({})
  const syncingByInstanz = ref<Record<string, boolean>>({})
  const lastSyncResultByInstanz = ref<Record<string, SvwsSchemaSyncResult | null>>({})

  // Je Fund-ID statt je Instanz-ID indiziert (ADR-014 Schritt 2): Zuordnung und SchulInfo
  // betreffen genau einen Fund im Zuordnungs-Modal, unabhängig von der übergeordneten Instanz.
  const assigningByFund = ref<Record<string, boolean>>({})
  const assignErrorByFund = ref<Record<string, string | null>>({})
  const schulInfoByFund = ref<Record<string, SvwsSchulInfoResult | null>>({})
  const schulInfoLoadingByFund = ref<Record<string, boolean>>({})

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchFunde(instanzId: string): Promise<void> {
    loadingByInstanz.value = { ...loadingByInstanz.value, [instanzId]: true }
    errorByInstanz.value = { ...errorByInstanz.value, [instanzId]: null }
    try {
      const result = await svwsSchemaFundApi.listSvwsInstanzSchemaFunde(instanzId, accessToken)
      itemsByInstanz.value = { ...itemsByInstanz.value, [instanzId]: result }
    } catch (error) {
      errorByInstanz.value = { ...errorByInstanz.value, [instanzId]: toErrorMessage(error) }
    } finally {
      loadingByInstanz.value = { ...loadingByInstanz.value, [instanzId]: false }
    }
  }

  /** Löst den Sync aus (ADR-014: liest nur, verändert keine SVWS-Daten) und lädt die Funde danach neu. */
  async function sync(instanzId: string): Promise<SvwsSchemaSyncResult> {
    syncingByInstanz.value = { ...syncingByInstanz.value, [instanzId]: true }
    errorByInstanz.value = { ...errorByInstanz.value, [instanzId]: null }
    try {
      const result = await svwsSchemaFundApi.syncSvwsInstanzSchemas(instanzId, accessToken)
      lastSyncResultByInstanz.value = { ...lastSyncResultByInstanz.value, [instanzId]: result }
      await fetchFunde(instanzId)
      return result
    } catch (error) {
      errorByInstanz.value = { ...errorByInstanz.value, [instanzId]: toErrorMessage(error) }
      throw error
    } finally {
      syncingByInstanz.value = { ...syncingByInstanz.value, [instanzId]: false }
    }
  }

  /**
   * Ordnet einen unzugeordneten Fund kontrolliert einer Schule zu (ADR-014 Schritt 2) und lädt
   * die Fund-Liste der Instanz danach neu, damit der Fund sofort als BEKANNT erscheint.
   */
  async function assign(instanzId: string, fundId: string, data: SchemaFundZuordnungFormData): Promise<SvwsSchemaFund> {
    assigningByFund.value = { ...assigningByFund.value, [fundId]: true }
    assignErrorByFund.value = { ...assignErrorByFund.value, [fundId]: null }
    try {
      const updated = await svwsSchemaFundApi.assignSchemaFund(instanzId, fundId, data, accessToken)
      await fetchFunde(instanzId)
      return updated
    } catch (error) {
      assignErrorByFund.value = { ...assignErrorByFund.value, [fundId]: toErrorMessage(error) }
      throw error
    } finally {
      assigningByFund.value = { ...assigningByFund.value, [fundId]: false }
    }
  }

  /**
   * Lädt optional die SVWS-Schulinformationen zum Fund (ADR-014 Schritt 2: rein informativ). Ein
   * Fehlschlag wird bewusst nicht als Store-Fehler behandelt - die manuelle Zuordnung darf davon
   * unberührt bleiben; der Aufrufer erkennt einen Fehlschlag an `schulInfoByFund[fundId]?.success`.
   */
  async function fetchSchulInfo(instanzId: string, fundId: string): Promise<void> {
    schulInfoLoadingByFund.value = { ...schulInfoLoadingByFund.value, [fundId]: true }
    try {
      const result = await svwsSchemaFundApi.getSchemaFundSchulInfo(instanzId, fundId, accessToken)
      schulInfoByFund.value = { ...schulInfoByFund.value, [fundId]: result }
    } catch (error) {
      schulInfoByFund.value = {
        ...schulInfoByFund.value,
        [fundId]: { success: false, message: toErrorMessage(error), schulInfo: null },
      }
    } finally {
      schulInfoLoadingByFund.value = { ...schulInfoLoadingByFund.value, [fundId]: false }
    }
  }

  return {
    itemsByInstanz,
    loadingByInstanz,
    errorByInstanz,
    syncingByInstanz,
    lastSyncResultByInstanz,
    assigningByFund,
    assignErrorByFund,
    schulInfoByFund,
    schulInfoLoadingByFund,
    fetchFunde,
    sync,
    assign,
    fetchSchulInfo,
  }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
