import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { SchemaUmgebung, SchemaUmgebungCreateFormData, SchemaUmgebungUpdateFormData } from '@/types/schemaUmgebung'

const BASE_PATH = '/schema-umgebungen'

export function listSchemaUmgebungen(getAccessToken: AccessTokenProvider): Promise<SchemaUmgebung[]> {
  return apiRequest<SchemaUmgebung[]>(BASE_PATH, { getAccessToken })
}

export function createSchemaUmgebung(
  data: SchemaUmgebungCreateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<SchemaUmgebung> {
  return apiRequest<SchemaUmgebung>(BASE_PATH, { method: 'POST', body: data, getAccessToken })
}

export function updateSchemaUmgebung(
  id: string,
  data: SchemaUmgebungUpdateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<SchemaUmgebung> {
  return apiRequest<SchemaUmgebung>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, getAccessToken })
}

export function deactivateSchemaUmgebung(id: string, getAccessToken: AccessTokenProvider): Promise<SchemaUmgebung> {
  return apiRequest<SchemaUmgebung>(`${BASE_PATH}/${id}`, { method: 'DELETE', getAccessToken })
}
