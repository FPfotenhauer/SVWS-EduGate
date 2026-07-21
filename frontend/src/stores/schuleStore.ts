import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schuleApi from '@/api/schuleApi'
import { ApiError } from '@/types/problem'
import type { Schule, SchuleFormData } from '@/types/schule'

export const useSchuleStore = defineStore('schule', () => {
  const items = ref<Schule[]>([])
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(schultraegerId: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      items.value = await schuleApi.listSchulen(schultraegerId, accessToken)
    } catch (error) {
      errorMessage.value = toErrorMessage(error)
    } finally {
      loading.value = false
    }
  }

  async function create(schultraegerId: string, data: SchuleFormData): Promise<Schule> {
    const created = await schuleApi.createSchule(schultraegerId, data, accessToken)
    await fetchList(schultraegerId)
    return created
  }

  async function update(schultraegerId: string, id: string, data: SchuleFormData): Promise<Schule> {
    const updated = await schuleApi.updateSchule(schultraegerId, id, data, accessToken)
    await fetchList(schultraegerId)
    return updated
  }

  function get(schultraegerId: string, id: string): Promise<Schule> {
    return schuleApi.getSchule(schultraegerId, id, accessToken)
  }

  async function deactivate(schultraegerId: string, id: string): Promise<Schule> {
    const deactivated = await schuleApi.deactivateSchule(schultraegerId, id, accessToken)
    await fetchList(schultraegerId)
    return deactivated
  }

  async function deleteEndgueltig(schultraegerId: string, id: string): Promise<void> {
    await schuleApi.deleteSchuleEndgueltig(schultraegerId, id, accessToken)
    await fetchList(schultraegerId)
  }

  return { items, loading, errorMessage, fetchList, create, update, get, deactivate, deleteEndgueltig }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
