import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { SvwsSchemaFund, SvwsSchemaSyncResult } from '@/types/svwsSchemaFund'

const BASE_PATH = '/svws-instanzen'

export function syncSvwsInstanzSchemas(
  instanzId: string,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsSchemaSyncResult> {
  return apiRequest<SvwsSchemaSyncResult>(`${BASE_PATH}/${instanzId}/schema-sync`, {
    method: 'POST',
    getAccessToken,
  })
}

export function listSvwsInstanzSchemaFunde(
  instanzId: string,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsSchemaFund[]> {
  return apiRequest<SvwsSchemaFund[]>(`${BASE_PATH}/${instanzId}/schema-funde`, { getAccessToken })
}
