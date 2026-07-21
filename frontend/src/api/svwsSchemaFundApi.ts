import { apiRequest, type AccessTokenProvider } from './httpClient'
import type {
  SchemaFundZuordnungFormData,
  SvwsSchemaFund,
  SvwsSchemaSyncResult,
  SvwsSchulInfoResult,
} from '@/types/svwsSchemaFund'

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

/** Ordnet einen unzugeordneten Fund kontrolliert einer Schule zu (ADR-014 Schritt 2). */
export function assignSchemaFund(
  instanzId: string,
  fundId: string,
  data: SchemaFundZuordnungFormData,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsSchemaFund> {
  return apiRequest<SvwsSchemaFund>(`${BASE_PATH}/${instanzId}/schema-funde/${fundId}/zuordnung`, {
    method: 'POST',
    body: data,
    getAccessToken,
  })
}

/** Rein informativ (ADR-014 Schritt 2) - liefert immer 200, auch bei SVWS-seitigem Fehlschlag. */
export function getSchemaFundSchulInfo(
  instanzId: string,
  fundId: string,
  getAccessToken: AccessTokenProvider,
): Promise<SvwsSchulInfoResult> {
  return apiRequest<SvwsSchulInfoResult>(`${BASE_PATH}/${instanzId}/schema-funde/${fundId}/schulinfo`, {
    getAccessToken,
  })
}
