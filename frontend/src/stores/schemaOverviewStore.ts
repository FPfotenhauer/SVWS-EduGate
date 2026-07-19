import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schemaOverviewApi from '@/api/schemaOverviewApi'
import { ApiError } from '@/types/problem'
import type { SchemaStatus } from '@/types/schema'
import type { SchemaOverviewFilter, SchemaOverviewItem } from '@/types/schemaOverview'

export const useSchemaOverviewStore = defineStore('schemaOverview', () => {
  const items = ref<SchemaOverviewItem[]>([])
  const page = ref(0)
  const size = ref(25)
  const totalElements = ref(0)
  const instanzId = ref('')
  const schultraegerId = ref('')
  const umgebung = ref('')
  const status = ref<SchemaStatus | ''>('')
  const query = ref('')
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(options: SchemaOverviewFilter & { size?: number } = {}): Promise<void> {
    loading.value = true
    errorMessage.value = null
    if (options.page !== undefined) page.value = options.page
    if (options.size !== undefined) size.value = options.size
    if (options.instanzId !== undefined) instanzId.value = options.instanzId
    if (options.schultraegerId !== undefined) schultraegerId.value = options.schultraegerId
    if (options.umgebung !== undefined) umgebung.value = options.umgebung
    if (options.status !== undefined) status.value = options.status
    if (options.q !== undefined) query.value = options.q

    try {
      const result = await schemaOverviewApi.listSchemaOverview(
        {
          page: page.value,
          size: size.value,
          instanzId: instanzId.value || undefined,
          schultraegerId: schultraegerId.value || undefined,
          umgebung: umgebung.value || undefined,
          status: status.value || undefined,
          q: query.value || undefined,
        },
        accessToken,
      )
      items.value = result.items
      page.value = result.page
      size.value = result.size
      totalElements.value = result.totalElements
    } catch (error) {
      errorMessage.value = toErrorMessage(error)
    } finally {
      loading.value = false
    }
  }

  return {
    items,
    page,
    size,
    totalElements,
    instanzId,
    schultraegerId,
    umgebung,
    status,
    query,
    loading,
    errorMessage,
    fetchList,
  }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
