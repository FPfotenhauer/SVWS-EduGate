import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchemaOverviewStore } from './schemaOverviewStore'
import * as schemaOverviewApi from '@/api/schemaOverviewApi'
import { ApiError } from '@/types/problem'
import type { SchemaOverviewItem } from '@/types/schemaOverview'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const beispielEintrag: SchemaOverviewItem = {
  id: '11111111-1111-1111-1111-111111111111',
  schemaName: '123456',
  umgebung: 'PRODUKTIV',
  status: 'GEPLANT',
  aktiv: true,
  beschreibung: null,
  source: 'MANUELL',
  lastSyncedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  schuleId: '22222222-2222-2222-2222-222222222222',
  schulnummer: '123456',
  schuleName: 'Musterschule',
  schultraegerId: '33333333-3333-3333-3333-333333333333',
  schultraegerName: 'Musterträger',
  instanzId: '44444444-4444-4444-4444-444444444444',
  instanzName: 'Instanz A',
  instanzBaseUrl: 'https://svws.example.org',
  instanzStatus: 'OK',
}

describe('schemaOverviewStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items und Paginierung bei Erfolg', async () => {
    vi.spyOn(schemaOverviewApi, 'listSchemaOverview').mockResolvedValue({
      items: [beispielEintrag],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSchemaOverviewStore()
    await store.fetchList()

    expect(store.items).toEqual([beispielEintrag])
    expect(store.totalElements).toBe(1)
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError', async () => {
    vi.spyOn(schemaOverviewApi, 'listSchemaOverview').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchemaOverviewStore()
    await store.fetchList()

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('fetchList übernimmt Filter und reicht sie an die API weiter', async () => {
    const listSpy = vi
      .spyOn(schemaOverviewApi, 'listSchemaOverview')
      .mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })

    const store = useSchemaOverviewStore()
    await store.fetchList({ instanzId: beispielEintrag.instanzId, umgebung: 'TEST', status: 'AKTIV', q: 'such' })

    expect(listSpy).toHaveBeenCalledWith(
      {
        page: 0,
        size: 25,
        instanzId: beispielEintrag.instanzId,
        schultraegerId: undefined,
        umgebung: 'TEST',
        status: 'AKTIV',
        q: 'such',
      },
      expect.any(Function),
    )
    expect(store.instanzId).toBe(beispielEintrag.instanzId)
    expect(store.umgebung).toBe('TEST')
    expect(store.status).toBe('AKTIV')
    expect(store.query).toBe('such')
  })

  it('fetchList behält gesetzte Filter über mehrere Aufrufe hinweg bei', async () => {
    const listSpy = vi
      .spyOn(schemaOverviewApi, 'listSchemaOverview')
      .mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })

    const store = useSchemaOverviewStore()
    await store.fetchList({ schultraegerId: beispielEintrag.schultraegerId })
    await store.fetchList({ page: 1 })

    expect(listSpy).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, schultraegerId: beispielEintrag.schultraegerId }),
      expect.any(Function),
    )
  })
})
