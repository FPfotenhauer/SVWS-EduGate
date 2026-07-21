import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as svwsSchemaFundApi from '@/api/svwsSchemaFundApi'
import { ApiError } from '@/types/problem'
import type { SvwsSchemaFund, SvwsSchemaSyncResult } from '@/types/svwsSchemaFund'

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

  return {
    itemsByInstanz,
    loadingByInstanz,
    errorByInstanz,
    syncingByInstanz,
    lastSyncResultByInstanz,
    fetchFunde,
    sync,
  }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
