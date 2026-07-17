import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schultraegerApi from '@/api/schultraegerApi'
import { ApiError } from '@/types/problem'
import type { Schultraeger, SchultraegerFormData } from '@/types/schultraeger'

export const useSchultraegerStore = defineStore('schultraeger', () => {
  const items = ref<Schultraeger[]>([])
  const page = ref(0)
  const size = ref(25)
  const totalElements = ref(0)
  const query = ref('')
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(options: { page?: number; q?: string } = {}): Promise<void> {
    loading.value = true
    errorMessage.value = null
    if (options.page !== undefined) page.value = options.page
    if (options.q !== undefined) query.value = options.q

    try {
      const result = await schultraegerApi.listSchultraeger(
        { page: page.value, size: size.value, q: query.value || undefined },
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

  async function create(data: SchultraegerFormData): Promise<Schultraeger> {
    const created = await schultraegerApi.createSchultraeger(data, accessToken)
    await fetchList()
    return created
  }

  async function update(id: string, data: SchultraegerFormData): Promise<Schultraeger> {
    const updated = await schultraegerApi.updateSchultraeger(id, data, accessToken)
    await fetchList()
    return updated
  }

  async function deactivate(id: string): Promise<void> {
    await schultraegerApi.deactivateSchultraeger(id, accessToken)
    await fetchList()
  }

  function get(id: string): Promise<Schultraeger> {
    return schultraegerApi.getSchultraeger(id, accessToken)
  }

  return { items, page, size, totalElements, query, loading, errorMessage, fetchList, create, update, deactivate, get }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
