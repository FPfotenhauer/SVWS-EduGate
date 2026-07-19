import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/auth/authStore'
import * as schemaApi from '@/api/schemaApi'
import { ApiError } from '@/types/problem'
import type { Schema, SchemaCreateFormData, SchemaNamingSuggestion, SchemaUpdateFormData } from '@/types/schema'

export const useSchemaStore = defineStore('schema', () => {
  const items = ref<Schema[]>([])
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  function accessToken(): string | null {
    return useAuthStore().accessToken
  }

  async function fetchList(schultraegerId: string, schuleId: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      items.value = await schemaApi.listSchemata(schultraegerId, schuleId, accessToken)
    } catch (error) {
      errorMessage.value = toErrorMessage(error)
    } finally {
      loading.value = false
    }
  }

  async function create(schultraegerId: string, schuleId: string, data: SchemaCreateFormData): Promise<Schema> {
    const created = await schemaApi.createSchema(schultraegerId, schuleId, data, accessToken)
    await fetchList(schultraegerId, schuleId)
    return created
  }

  async function update(
    schultraegerId: string,
    schuleId: string,
    id: string,
    data: SchemaUpdateFormData,
  ): Promise<Schema> {
    const updated = await schemaApi.updateSchema(schultraegerId, schuleId, id, data, accessToken)
    await fetchList(schultraegerId, schuleId)
    return updated
  }

  async function deactivate(schultraegerId: string, schuleId: string, id: string): Promise<void> {
    await schemaApi.deactivateSchema(schultraegerId, schuleId, id, accessToken)
    await fetchList(schultraegerId, schuleId)
  }

  function namingSuggestion(
    schultraegerId: string,
    schuleId: string,
    umgebung: string,
  ): Promise<SchemaNamingSuggestion> {
    return schemaApi.schemaNamingSuggestion(schultraegerId, schuleId, umgebung, accessToken)
  }

  return { items, loading, errorMessage, fetchList, create, update, deactivate, namingSuggestion }
})

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return 'Unerwarteter Fehler bei der Kommunikation mit dem Server.'
}
