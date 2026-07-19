import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { SchemaOverviewFilter, SchemaOverviewPage } from '@/types/schemaOverview'

const BASE_PATH = '/schuldatenbanken'

export function listSchemaOverview(
  params: SchemaOverviewFilter & { size: number },
  getAccessToken: AccessTokenProvider,
): Promise<SchemaOverviewPage> {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size) })
  if (params.instanzId) query.set('instanzId', params.instanzId)
  if (params.schultraegerId) query.set('schultraegerId', params.schultraegerId)
  if (params.umgebung) query.set('umgebung', params.umgebung)
  if (params.status) query.set('status', params.status)
  if (params.q) query.set('q', params.q)
  return apiRequest<SchemaOverviewPage>(`${BASE_PATH}?${query.toString()}`, { getAccessToken })
}
