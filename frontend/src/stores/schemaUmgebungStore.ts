import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schemaUmgebungApi from '@/api/schemaUmgebungApi'
import { ApiError } from '@/types/problem'
import type { SchemaUmgebung, SchemaUmgebungCreateFormData, SchemaUmgebungUpdateFormData } from '@/types/schemaUmgebung'

export const useSchemaUmgebungStore = defineStore('schemaUmgebung', () => {
  const items = ref<SchemaUmgebung[]>([])
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      items.value = await schemaUmgebungApi.listSchemaUmgebungen(accessToken)
    } catch (error) {
      errorMessage.value = toErrorMessage(error)
    } finally {
      loading.value = false
    }
  }

  async function create(data: SchemaUmgebungCreateFormData): Promise<SchemaUmgebung> {
    const created = await schemaUmgebungApi.createSchemaUmgebung(data, accessToken)
    await fetchList()
    return created
  }

  async function update(id: string, data: SchemaUmgebungUpdateFormData): Promise<SchemaUmgebung> {
    const updated = await schemaUmgebungApi.updateSchemaUmgebung(id, data, accessToken)
    await fetchList()
    return updated
  }

  async function deactivate(id: string): Promise<void> {
    await schemaUmgebungApi.deactivateSchemaUmgebung(id, accessToken)
    await fetchList()
  }

  return { items, loading, errorMessage, fetchList, create, update, deactivate }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
