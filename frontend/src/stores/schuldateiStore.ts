import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schuldateiApi from '@/api/schuldateiApi'
import { ApiError } from '@/types/problem'
import type { SchuldateiImportStatus, SchuleKatalog, SchultraegerKatalog } from '@/types/schuldatei'

/**
 * Landes-Schuldatei als Referenzkatalog (ADR-020): Importstatus, durchsuchbare/filterbare
 * Schulen- und Schulträger-Listen. Der Refresh ist eine geschützte, auditierte
 * Betreiber-Aktion (ADR-020-Nachtrag) - kein automatischer Abruf beim Öffnen der Kachel.
 */
export const useSchuldateiStore = defineStore('schuldatei', () => {
  const status = ref<SchuldateiImportStatus | null>(null)
  const statusLoading = ref(false)
  const statusError = ref<string | null>(null)
  const refreshing = ref(false)
  const refreshError = ref<string | null>(null)

  const schulen = ref<SchuleKatalog[]>([])
  const schulenPage = ref(0)
  const schulenSize = ref(25)
  const schulenTotalElements = ref(0)
  const schulenQuery = ref('')
  const schulenSchultraegernummer = ref('')
  const schulenNurAktive = ref<boolean | undefined>(undefined)
  const schulenLoading = ref(false)
  const schulenError = ref<string | null>(null)

  const schultraeger = ref<SchultraegerKatalog[]>([])
  const schultraegerPage = ref(0)
  const schultraegerSize = ref(25)
  const schultraegerTotalElements = ref(0)
  const schultraegerQuery = ref('')
  const schultraegerNurAktive = ref<boolean | undefined>(undefined)
  const schultraegerLoading = ref(false)
  const schultraegerError = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchStatus(): Promise<void> {
    statusLoading.value = true
    statusError.value = null
    try {
      status.value = await schuldateiApi.getSchuldateiStatus(accessToken)
    } catch (error) {
      statusError.value = toErrorMessage(error)
    } finally {
      statusLoading.value = false
    }
  }

  /** Löst den Refresh aus und aktualisiert den Status - lädt Schulen/Schulträger absichtlich nicht automatisch neu. */
  async function refresh(): Promise<SchuldateiImportStatus> {
    refreshing.value = true
    refreshError.value = null
    try {
      const result = await schuldateiApi.refreshSchuldatei(accessToken)
      status.value = result
      return result
    } catch (error) {
      refreshError.value = toErrorMessage(error)
      throw error
    } finally {
      refreshing.value = false
    }
  }

  async function fetchSchulen(
    options: { page?: number; q?: string; schultraegernummer?: string; nurAktive?: boolean } = {},
  ): Promise<void> {
    schulenLoading.value = true
    schulenError.value = null
    if (options.page !== undefined) schulenPage.value = options.page
    if (options.q !== undefined) schulenQuery.value = options.q
    if (options.schultraegernummer !== undefined) schulenSchultraegernummer.value = options.schultraegernummer
    if (options.nurAktive !== undefined) schulenNurAktive.value = options.nurAktive
    try {
      const result = await schuldateiApi.listSchuleKatalog(
        {
          page: schulenPage.value,
          size: schulenSize.value,
          q: schulenQuery.value || undefined,
          schultraegernummer: schulenSchultraegernummer.value || undefined,
          nurAktive: schulenNurAktive.value,
        },
        accessToken,
      )
      schulen.value = result.items
      schulenPage.value = result.page
      schulenSize.value = result.size
      schulenTotalElements.value = result.totalElements
    } catch (error) {
      schulenError.value = toErrorMessage(error)
    } finally {
      schulenLoading.value = false
    }
  }

  async function fetchSchultraeger(options: { page?: number; q?: string; nurAktive?: boolean } = {}): Promise<void> {
    schultraegerLoading.value = true
    schultraegerError.value = null
    if (options.page !== undefined) schultraegerPage.value = options.page
    if (options.q !== undefined) schultraegerQuery.value = options.q
    if (options.nurAktive !== undefined) schultraegerNurAktive.value = options.nurAktive
    try {
      const result = await schuldateiApi.listSchultraegerKatalog(
        {
          page: schultraegerPage.value,
          size: schultraegerSize.value,
          q: schultraegerQuery.value || undefined,
          nurAktive: schultraegerNurAktive.value,
        },
        accessToken,
      )
      schultraeger.value = result.items
      schultraegerPage.value = result.page
      schultraegerSize.value = result.size
      schultraegerTotalElements.value = result.totalElements
    } catch (error) {
      schultraegerError.value = toErrorMessage(error)
    } finally {
      schultraegerLoading.value = false
    }
  }

  return {
    status,
    statusLoading,
    statusError,
    refreshing,
    refreshError,
    schulen,
    schulenPage,
    schulenSize,
    schulenTotalElements,
    schulenQuery,
    schulenSchultraegernummer,
    schulenNurAktive,
    schulenLoading,
    schulenError,
    schultraeger,
    schultraegerPage,
    schultraegerSize,
    schultraegerTotalElements,
    schultraegerQuery,
    schultraegerNurAktive,
    schultraegerLoading,
    schultraegerError,
    fetchStatus,
    refresh,
    fetchSchulen,
    fetchSchultraeger,
  }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
