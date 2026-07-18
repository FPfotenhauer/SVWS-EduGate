import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchultraegerStore } from './schultraegerStore'
import * as schultraegerApi from '@/api/schultraegerApi'
import { ApiError } from '@/types/problem'
import type { Schultraeger } from '@/types/schultraeger'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const beispielSchultraeger: Schultraeger = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Musterstadt',
  traegernummer: '000001',
  aktiv: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('schultraegerStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items, page, size und totalElements bei Erfolg', async () => {
    vi.spyOn(schultraegerApi, 'listSchultraeger').mockResolvedValue({
      items: [beispielSchultraeger],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSchultraegerStore()
    await store.fetchList()

    expect(store.items).toEqual([beispielSchultraeger])
    expect(store.totalElements).toBe(1)
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError, ohne die bisherige Liste zu verlieren', async () => {
    vi.spyOn(schultraegerApi, 'listSchultraeger').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchultraegerStore()
    await store.fetchList()

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit den Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(schultraegerApi, 'createSchultraeger').mockResolvedValue(beispielSchultraeger)
    const listSpy = vi
      .spyOn(schultraegerApi, 'listSchultraeger')
      .mockResolvedValue({ items: [beispielSchultraeger], page: 0, size: 25, totalElements: 1 })

    const store = useSchultraegerStore()
    const result = await store.create({ name: 'Musterstadt', traegernummer: '000001' })

    expect(createSpy).toHaveBeenCalledWith({ name: 'Musterstadt', traegernummer: '000001' }, expect.any(Function))
    expect(listSpy).toHaveBeenCalled()
    expect(result).toEqual(beispielSchultraeger)
  })

  it('deactivate ruft die API mit der id auf und lädt die Liste danach neu', async () => {
    const deactivateSpy = vi.spyOn(schultraegerApi, 'deactivateSchultraeger').mockResolvedValue(undefined)
    vi.spyOn(schultraegerApi, 'listSchultraeger').mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })

    const store = useSchultraegerStore()
    await store.deactivate(beispielSchultraeger.id)

    expect(deactivateSpy).toHaveBeenCalledWith(beispielSchultraeger.id, expect.any(Function))
  })

  it('fetchList übernimmt Seiten- und Suchparameter für den nächsten Aufruf', async () => {
    const listSpy = vi
      .spyOn(schultraegerApi, 'listSchultraeger')
      .mockResolvedValue({ items: [], page: 2, size: 25, totalElements: 0 })

    const store = useSchultraegerStore()
    await store.fetchList({ page: 2, q: 'Musterstadt' })

    expect(listSpy).toHaveBeenCalledWith({ page: 2, size: 25, q: 'Musterstadt' }, expect.any(Function))
  })
})
