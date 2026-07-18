import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { Ansprechpartner, AnsprechpartnerFormData } from '@/types/ansprechpartner'

function basePath(schultraegerId: string): string {
  return `/schultraeger/${schultraegerId}/ansprechpartner`
}

export function listAnsprechpartner(
  schultraegerId: string,
  getAccessToken: AccessTokenProvider,
): Promise<Ansprechpartner[]> {
  return apiRequest<Ansprechpartner[]>(basePath(schultraegerId), { getAccessToken })
}

export function createAnsprechpartner(
  schultraegerId: string,
  data: AnsprechpartnerFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Ansprechpartner> {
  return apiRequest<Ansprechpartner>(basePath(schultraegerId), { method: 'POST', body: data, getAccessToken })
}

export function updateAnsprechpartner(
  schultraegerId: string,
  id: string,
  data: AnsprechpartnerFormData,
  getAccessToken: AccessTokenProvider,
): Promise<Ansprechpartner> {
  return apiRequest<Ansprechpartner>(`${basePath(schultraegerId)}/${id}`, {
    method: 'PUT',
    body: data,
    getAccessToken,
  })
}

export function deleteAnsprechpartner(
  schultraegerId: string,
  id: string,
  getAccessToken: AccessTokenProvider,
): Promise<void> {
  return apiRequest<void>(`${basePath(schultraegerId)}/${id}`, { method: 'DELETE', getAccessToken })
}
