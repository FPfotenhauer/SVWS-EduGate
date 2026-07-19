import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { Schule, SchuleFormData } from '@/types/schule'

function basePath(schultraegerId: string): string {
  return `/schultraeger/${schultraegerId}/schulen`
}

export function listSchulen(schultraegerId: string, getAccessToken: AccessTokenProvider): Promise<Schule[]> {
  return apiRequest<Schule[]>(basePath(schultraegerId), { getAccessToken })
}

export function getSchule(schultraegerId: string, id: string, getAccessToken: AccessTokenProvider): Promise<Schule> {
  return apiRequest<Schule>(`${basePath(schultraegerId)}/${id}`, { getAccessToken })
}

export function createSchule(
  schultraegerId: string,
  data: SchuleFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schule> {
  return apiRequest<Schule>(basePath(schultraegerId), { method: 'POST', body: data, getAccessToken })
}

export function updateSchule(
  schultraegerId: string,
  id: string,
  data: SchuleFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Schule> {
  return apiRequest<Schule>(`${basePath(schultraegerId)}/${id}`, { method: 'PUT', body: data, getAccessToken })
}
