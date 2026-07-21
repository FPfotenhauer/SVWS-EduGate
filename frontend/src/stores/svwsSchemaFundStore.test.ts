import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSvwsSchemaFundStore } from './svwsSchemaFundStore'
import * as svwsSchemaFundApi from '@/api/svwsSchemaFundApi'
import { ApiError } from '@/types/problem'
import type { SvwsSchemaFund, SvwsSchemaSyncResult } from '@/types/svwsSchemaFund'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const instanzId = '11111111-1111-1111-1111-111111111111'

const beispielFund: SvwsSchemaFund = {
  id: '22222222-2222-2222-2222-222222222222',
  instanzId,
  schemaName: '123456',
  username: '123456',
  isSvws: true,
  revision: 3,
  isTainted: false,
  isInConfig: true,
  isDeactivated: false,
  firstSeenAt: '2026-01-01T00:00:00Z',
  lastSeenAt: '2026-01-01T00:00:00Z',
  zuordnungsStatus: 'UNZUGEORDNET',
  schemaId: null,
  schuleId: null,
  schultraegerId: null,
}

describe('svwsSchemaFundStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchFunde befüllt itemsByInstanz für die jeweilige Instanz', async () => {
    vi.spyOn(svwsSchemaFundApi, 'listSvwsInstanzSchemaFunde').mockResolvedValue([beispielFund])

    const store = useSvwsSchemaFundStore()
    await store.fetchFunde(instanzId)

    expect(store.itemsByInstanz[instanzId]).toEqual([beispielFund])
    expect(store.loadingByInstanz[instanzId]).toBe(false)
    expect(store.errorByInstanz[instanzId]).toBeNull()
  })

  it('fetchFunde setzt errorByInstanz bei einem ApiError', async () => {
    vi.spyOn(svwsSchemaFundApi, 'listSvwsInstanzSchemaFunde').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSvwsSchemaFundStore()
    await store.fetchFunde(instanzId)

    expect(store.errorByInstanz[instanzId]).toContain('kaputt')
    expect(store.loadingByInstanz[instanzId]).toBe(false)
  })

  it('sync ruft die API auf, merkt sich das Ergebnis und lädt die Funde neu', async () => {
    const syncResult: SvwsSchemaSyncResult = {
      success: true,
      message: 'Schema-Liste erfolgreich abgerufen.',
      gefundeneSchemata: 1,
      syncedAt: '2026-01-02T00:00:00Z',
    }
    const syncSpy = vi.spyOn(svwsSchemaFundApi, 'syncSvwsInstanzSchemas').mockResolvedValue(syncResult)
    const listSpy = vi.spyOn(svwsSchemaFundApi, 'listSvwsInstanzSchemaFunde').mockResolvedValue([beispielFund])

    const store = useSvwsSchemaFundStore()
    const result = await store.sync(instanzId)

    expect(syncSpy).toHaveBeenCalledWith(instanzId, expect.any(Function))
    expect(listSpy).toHaveBeenCalledWith(instanzId, expect.any(Function))
    expect(result).toEqual(syncResult)
    expect(store.lastSyncResultByInstanz[instanzId]).toEqual(syncResult)
    expect(store.itemsByInstanz[instanzId]).toEqual([beispielFund])
    expect(store.syncingByInstanz[instanzId]).toBe(false)
  })

  it('sync wirft den Fehler weiter und setzt errorByInstanz, ohne die Funde neu zu laden', async () => {
    vi.spyOn(svwsSchemaFundApi, 'syncSvwsInstanzSchemas').mockRejectedValue(
      new ApiError(404, {
        type: 'urn:problem-type:not-found',
        title: 'Nicht gefunden',
        status: 404,
        detail: 'weg',
        instance: null,
      }),
    )
    const listSpy = vi.spyOn(svwsSchemaFundApi, 'listSvwsInstanzSchemaFunde')

    const store = useSvwsSchemaFundStore()
    await expect(store.sync(instanzId)).rejects.toBeInstanceOf(ApiError)

    expect(store.errorByInstanz[instanzId]).toContain('weg')
    expect(store.syncingByInstanz[instanzId]).toBe(false)
    expect(listSpy).not.toHaveBeenCalled()
  })

  it('fetchFunde und sync halten den Zustand mehrerer Instanzen getrennt', async () => {
    const andereInstanzId = '33333333-3333-3333-3333-333333333333'
    vi.spyOn(svwsSchemaFundApi, 'listSvwsInstanzSchemaFunde').mockImplementation((id) =>
      Promise.resolve(id === instanzId ? [beispielFund] : []),
    )

    const store = useSvwsSchemaFundStore()
    await Promise.all([store.fetchFunde(instanzId), store.fetchFunde(andereInstanzId)])

    expect(store.itemsByInstanz[instanzId]).toEqual([beispielFund])
    expect(store.itemsByInstanz[andereInstanzId]).toEqual([])
  })
})
