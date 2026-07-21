import { apiRequest, type AccessTokenProvider } from './httpClient'
import type { SchuldateiImportStatus, SchuleKatalogPage, SchultraegerKatalogPage } from '@/types/schuldatei'

const BASE_PATH = '/schuldatei'

/**
 * Liefert {@code null}, solange noch kein Import gelaufen ist. Normalisiert bewusst sowohl den
 * 204-Fall (kein Body) als auch einen möglichen JSON-"null"-Body auf denselben Rückgabewert.
 */
export async function getSchuldateiStatus(getAccessToken: AccessTokenProvider): Promise<SchuldateiImportStatus | null> {
  const result = await apiRequest<SchuldateiImportStatus | null | undefined>(`${BASE_PATH}/status`, { getAccessToken })
  return result ?? null
}

/** Löst einen geschützten, auditierten Schuldatei-Refresh aus (ADR-020-Nachtrag: Upsert, kein Clear-and-Insert). */
export function refreshSchuldatei(getAccessToken: AccessTokenProvider): Promise<SchuldateiImportStatus> {
  return apiRequest<SchuldateiImportStatus>(`${BASE_PATH}/refresh`, { method: 'POST', getAccessToken })
}

export function listSchuleKatalog(
  params: { page: number; size: number; q?: string; schultraegernummer?: string; nurAktive?: boolean },
  getAccessToken: AccessTokenProvider,
): Promise<SchuleKatalogPage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.q) {
    query.set('q', params.q)
  }
  if (params.schultraegernummer) {
    query.set('schultraegernummer', params.schultraegernummer)
  }
  if (params.nurAktive !== undefined) {
    query.set('nurAktive', String(params.nurAktive))
  }
  return apiRequest<SchuleKatalogPage>(`${BASE_PATH}/schulen?${query.toString()}`, { getAccessToken })
}

export function listSchultraegerKatalog(
  params: { page: number; size: number; q?: string; nurAktive?: boolean },
  getAccessToken: AccessTokenProvider,
): Promise<SchultraegerKatalogPage> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.q) {
    query.set('q', params.q)
  }
  if (params.nurAktive !== undefined) {
    query.set('nurAktive', String(params.nurAktive))
  }
  return apiRequest<SchultraegerKatalogPage>(`${BASE_PATH}/schultraeger?${query.toString()}`, { getAccessToken })
}
