import { apiRequest, type AccessTokenProvider } from './httpClient'
import type {
  Schema,
  SchemaCreateFormData,
  SchemaDestroyResult,
  SchemaNamingSuggestion,
  SchemaUpdateFormData,
} from '@/types/schema'

function basePath(schultraegerId: string, schuleId: string): string {
  return `/schultraeger/${schultraegerId}/schulen/${schuleId}/schemata`
}

export function listSchemata(
  schultraegerId: string,
  schuleId: string,
  getAccessToken: AccessTokenProvider,
): Promise<Schema[]> {
  return apiRequest<Schema[]>(basePath(schultraegerId, schuleId), { getAccessToken })
}

export function createSchema(
  schultraegerId: string,
  schuleId: string,
  data: SchemaCreateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schema> {
  return apiRequest<Schema>(basePath(schultraegerId, schuleId), { method: 'POST', body: data, getAccessToken })
}

export function updateSchema(
  schultraegerId: string,
  schuleId: string,
  id: string,
  data: SchemaUpdateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schema> {
  return apiRequest<Schema>(`${basePath(schultraegerId, schuleId)}/${id}`, {
    method: 'PUT',
    body: data,
    getAccessToken,
  })
}

export function deactivateSchema(
  schultraegerId: string,
  schuleId: string,
  id: string,
  getAccessToken: AccessTokenProvider,
): Promise<Schema> {
  return apiRequest<Schema>(`${basePath(schultraegerId, schuleId)}/${id}`, { method: 'DELETE', getAccessToken })
}

/** Löscht ein echtes Schema unwiderruflich über die SVWS-Privileged-API (ADR-014). */
export function destroySchemaOnInstanz(
  schultraegerId: string,
  schuleId: string,
  id: string,
  getAccessToken: AccessTokenProvider,
): Promise<SchemaDestroyResult> {
  return apiRequest<SchemaDestroyResult>(`${basePath(schultraegerId, schuleId)}/${id}/loeschen-auf-instanz`, {
    method: 'POST',
    getAccessToken,
  })
}

export function schemaNamingSuggestion(
  schultraegerId: string,
  schuleId: string,
  umgebung: string,
  getAccessToken: AccessTokenProvider,
): Promise<SchemaNamingSuggestion> {
  const query = new URLSearchParams({ umgebung })
  return apiRequest<SchemaNamingSuggestion>(
    `${basePath(schultraegerId, schuleId)}/namensvorschlag?${query.toString()}`,
    {
      getAccessToken,
    },
  )
}
