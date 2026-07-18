import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as svwsInstanzApi from '@/api/svwsInstanzApi'
import { ApiError } from '@/types/problem'
import type {
  SvwsInstanz,
  SvwsInstanzCreateFormData,
  SvwsInstanzCredentialsFormData,
  SvwsInstanzUpdateFormData,
} from '@/types/svwsInstanz'

export const useSvwsInstanzStore = defineStore('svwsInstanz', () => {
  const items = ref<SvwsInstanz[]>([])
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
      const result = await svwsInstanzApi.listSvwsInstanzen(
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

  async function create(data: SvwsInstanzCreateFormData): Promise<SvwsInstanz> {
    const created = await svwsInstanzApi.createSvwsInstanz(data, accessToken)
    await fetchList()
    return created
  }

  async function update(id: string, data: SvwsInstanzUpdateFormData): Promise<SvwsInstanz> {
    const updated = await svwsInstanzApi.updateSvwsInstanz(id, data, accessToken)
    await fetchList()
    return updated
  }

  async function setCredentials(id: string, data: SvwsInstanzCredentialsFormData): Promise<void> {
    await svwsInstanzApi.setSvwsInstanzCredentials(id, data, accessToken)
  }

  async function deactivate(id: string): Promise<void> {
    await svwsInstanzApi.deactivateSvwsInstanz(id, accessToken)
    await fetchList()
  }

  function get(id: string): Promise<SvwsInstanz> {
    return svwsInstanzApi.getSvwsInstanz(id, accessToken)
  }

  return {
    items,
    page,
    size,
    totalElements,
    query,
    loading,
    errorMessage,
    fetchList,
    create,
    update,
    setCredentials,
    deactivate,
    get,
  }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
