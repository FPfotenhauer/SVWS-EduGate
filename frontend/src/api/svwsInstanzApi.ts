import { apiRequest, type AccessTokenProvider } from './httpClient'
import type {
  SvwsInstanz,
  SvwsInstanzCreateFormData,
  SvwsInstanzCredentialsFormData,
  SvwsInstanzPage,
  SvwsInstanzUpdateFormData,
} from '@/types/svwsInstanz'

const BASE_PATH = '/svws-instanzen'

export function listSvwsInstanzen(
  params: { page: number; size: number; q?: string; status?: string },
  getAccessToken: AccessTokenProvider,
): Promise<SvwsInstanzPage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.q) {
    query.set('q', params.q)
  }
  if (params.status) {
    query.set('status', params.status)
  }
  return apiRequest<SvwsInstanzPage>(`${BASE_PATH}?${query.toString()}`, { getAccessToken })
}

export function getSvwsInstanz(id: string, getAccessToken: AccessTokenProvider): Promise<SvwsInstanz> {
  return apiRequest<SvwsInstanz>(`${BASE_PATH}/${id}`, { getAccessToken })
}

export function createSvwsInstanz(
  data: SvwsInstanzCreateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsInstanz> {
  return apiRequest<SvwsInstanz>(BASE_PATH, { method: 'POST', body: data, getAccessToken })
}

export function updateSvwsInstanz(
  id: string,
  data: SvwsInstanzUpdateFormData,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsInstanz> {
  return apiRequest<SvwsInstanz>(`${BASE_PATH}/${id}`, { method: 'PUT', body: data, getAccessToken })
}

export function setSvwsInstanzCredentials(
  id: string,
  data: SvwsInstanzCredentialsFormData,
  getAccessToken: AccessTokenProvider,
): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}/credentials`, { method: 'PUT', body: data, getAccessToken })
}

export function deactivateSvwsInstanz(id: string, getAccessToken: AccessTokenProvider): Promise<void> {
  return apiRequest<void>(`${BASE_PATH}/${id}`, { method: 'DELETE', getAccessToken })
}

export function testSvwsInstanzConnection(id: string, getAccessToken: AccessTokenProvider): Promise<SvwsInstanz> {
  return apiRequest<SvwsInstanz>(`${BASE_PATH}/${id}/connection-test`, { method: 'POST', getAccessToken })
}
