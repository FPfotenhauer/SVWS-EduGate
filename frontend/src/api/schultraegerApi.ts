import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { Schultraeger, SchultraegerFormData, SchultraegerPage } from '@/types/schultraeger'

const BASE_PATH = '/schultraeger'

export function listSchultraeger(
  params: { page: number; size: number; q?: string },
  getAccessToken: AccessTokenProvider,
): Promise<SchultraegerPage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.q) {
    query.set('q', params.q)
  }
  return apiRequest<SchultraegerPage>(`${BASE_PATH}?${query.toString()}`, { getAccessToken })
}

export function getSchultraeger(id: string, getAccessToken: AccessTokenProvider): Promise<Schultraeger> {
  return apiRequest<Schultraeger>(`${BASE_PATH}/${id}`, { getAccessToken })
}

export function createSchultraeger(
  data: SchultraegerFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schultraeger> {
  return apiRequest<Schultraeger>(BASE_PATH, { method: 'POST', body: data, getAccessToken })
}

export function updateSchultraeger(
  id: string,
  data: SchultraegerFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schultraeger> {
  return apiRequest<Schultraeger>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, getAccessToken })
}

export function deactivateSchultraeger(id: string, getAccessToken: AccessTokenProvider): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', getAccessToken })
}

export function reactivateSchultraeger(id: string, getAccessToken: AccessTokenProvider): Promise<Schultraeger> {
  return apiRequest<Schultraeger>(`${BASE_PATH}/${id}/reaktivieren`, { method: 'POST', getAccessToken })
}
