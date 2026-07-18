import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as ansprechpartnerApi from '@/api/ansprechpartnerApi'
import { ApiError } from '@/types/problem'
import type { Ansprechpartner, AnsprechpartnerFormData } from '@/types/ansprechpartner'

export const useAnsprechpartnerStore = defineStore('ansprechpartner', () => {
  const items = ref<Ansprechpartner[]>([])
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(schultraegerId: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      items.value = await ansprechpartnerApi.listAnsprechpartner(schultraegerId, accessToken)
    } catch (error) {
      errorMessage.value = toErrorMessage(error)
    } finally {
      loading.value = false
    }
  }

  async function create(schultraegerId: string, data: AnsprechpartnerFormData): Promise<Ansprechpartner> {
    const created = await ansprechpartnerApi.createAnsprechpartner(schultraegerId, data, accessToken)
    await fetchList(schultraegerId)
    return created
  }

  async function update(schultraegerId: string, id: string, data: AnsprechpartnerFormData): Promise<Ansprechpartner> {
    const updated = await ansprechpartnerApi.updateAnsprechpartner(schultraegerId, id, data, accessToken)
    await fetchList(schultraegerId)
    return updated
  }

  async function remove(schultraegerId: string, id: string): Promise<void> {
    await ansprechpartnerApi.deleteAnsprechpartner(schultraegerId, id, accessToken)
    await fetchList(schultraegerId)
  }

  return { items, loading, errorMessage, fetchList, create, update, remove }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
