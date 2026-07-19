import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchuleStore } from './schuleStore'
import * as schuleApi from '@/api/schuleApi'
import { ApiError } from '@/types/problem'
import type { Schule } from '@/types/schule'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const SCHULTRAEGER_ID = '11111111-1111-1111-1111-111111111111'

const beispielSchule: Schule = {
  id: '33333333-3333-3333-3333-333333333333',
  schultraegerId: SCHULTRAEGER_ID,
  schulnummer: '123456',
  name: 'Musterschule',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('schuleStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items bei Erfolg', async () => {
    vi.spyOn(schuleApi, 'listSchulen').mockResolvedValue([beispielSchule])

    const store = useSchuleStore()
    await store.fetchList(SCHULTRAEGER_ID)

    expect(store.items).toEqual([beispielSchule])
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError', async () => {
    vi.spyOn(schuleApi, 'listSchulen').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchuleStore()
    await store.fetchList(SCHULTRAEGER_ID)

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit Schulträger-ID und Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(schuleApi, 'createSchule').mockResolvedValue(beispielSchule)
    const listSpy = vi.spyOn(schuleApi, 'listSchulen').mockResolvedValue([beispielSchule])

    const store = useSchuleStore()
    const result = await store.create(SCHULTRAEGER_ID, { schulnummer: '123456', name: 'Musterschule' })

    expect(createSpy).toHaveBeenCalledWith(
      SCHULTRAEGER_ID,
      { schulnummer: '123456', name: 'Musterschule' },
      expect.any(Function),
    )
    expect(listSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, expect.any(Function))
    expect(result).toEqual(beispielSchule)
  })

  it('update ruft die API mit Schulträger-ID, Schul-ID und Formulardaten auf', async () => {
    const updateSpy = vi.spyOn(schuleApi, 'updateSchule').mockResolvedValue(beispielSchule)
    vi.spyOn(schuleApi, 'listSchulen').mockResolvedValue([beispielSchule])

    const store = useSchuleStore()
    await store.update(SCHULTRAEGER_ID, beispielSchule.id, { schulnummer: '123456', name: 'Musterschule (neu)' })

    expect(updateSpy).toHaveBeenCalledWith(
      SCHULTRAEGER_ID,
      beispielSchule.id,
      { schulnummer: '123456', name: 'Musterschule (neu)' },
      expect.any(Function),
    )
  })

  it('get ruft die API mit Schulträger-ID und Schul-ID auf', async () => {
    const getSpy = vi.spyOn(schuleApi, 'getSchule').mockResolvedValue(beispielSchule)

    const store = useSchuleStore()
    const result = await store.get(SCHULTRAEGER_ID, beispielSchule.id)

    expect(getSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, beispielSchule.id, expect.any(Function))
    expect(result).toEqual(beispielSchule)
  })
})
