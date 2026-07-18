import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAnsprechpartnerStore } from './ansprechpartnerStore'
import * as ansprechpartnerApi from '@/api/ansprechpartnerApi'
import { ApiError } from '@/types/problem'
import type { Ansprechpartner } from '@/types/ansprechpartner'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const SCHULTRAEGER_ID = '11111111-1111-1111-1111-111111111111'

const beispielAnsprechpartner: Ansprechpartner = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Mustermann',
  vorname: 'Max',
  titel: null,
  abteilung: null,
  funktion: null,
  email: null,
  telefonFestnetz: null,
  telefonMobil: null,
  beschreibung: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('ansprechpartnerStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items bei Erfolg', async () => {
    vi.spyOn(ansprechpartnerApi, 'listAnsprechpartner').mockResolvedValue([beispielAnsprechpartner])

    const store = useAnsprechpartnerStore()
    await store.fetchList(SCHULTRAEGER_ID)

    expect(store.items).toEqual([beispielAnsprechpartner])
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError', async () => {
    vi.spyOn(ansprechpartnerApi, 'listAnsprechpartner').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useAnsprechpartnerStore()
    await store.fetchList(SCHULTRAEGER_ID)

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit Schulträger-ID und Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(ansprechpartnerApi, 'createAnsprechpartner').mockResolvedValue(beispielAnsprechpartner)
    const listSpy = vi.spyOn(ansprechpartnerApi, 'listAnsprechpartner').mockResolvedValue([beispielAnsprechpartner])

    const store = useAnsprechpartnerStore()
    const result = await store.create(SCHULTRAEGER_ID, { name: 'Mustermann', vorname: 'Max' })

    expect(createSpy).toHaveBeenCalledWith(
      SCHULTRAEGER_ID,
      { name: 'Mustermann', vorname: 'Max' },
      expect.any(Function),
    )
    expect(listSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, expect.any(Function))
    expect(result).toEqual(beispielAnsprechpartner)
  })

  it('update ruft die API mit Schulträger-ID, Ansprechpartner-ID und Formulardaten auf', async () => {
    const updateSpy = vi.spyOn(ansprechpartnerApi, 'updateAnsprechpartner').mockResolvedValue(beispielAnsprechpartner)
    vi.spyOn(ansprechpartnerApi, 'listAnsprechpartner').mockResolvedValue([beispielAnsprechpartner])

    const store = useAnsprechpartnerStore()
    await store.update(SCHULTRAEGER_ID, beispielAnsprechpartner.id, { name: 'Mustermann', vorname: 'Maxine' })

    expect(updateSpy).toHaveBeenCalledWith(
      SCHULTRAEGER_ID,
      beispielAnsprechpartner.id,
      { name: 'Mustermann', vorname: 'Maxine' },
      expect.any(Function),
    )
  })

  it('remove ruft die API mit Schulträger-ID und Ansprechpartner-ID auf und lädt die Liste danach neu', async () => {
    const removeSpy = vi.spyOn(ansprechpartnerApi, 'deleteAnsprechpartner').mockResolvedValue(undefined)
    const listSpy = vi.spyOn(ansprechpartnerApi, 'listAnsprechpartner').mockResolvedValue([])

    const store = useAnsprechpartnerStore()
    await store.remove(SCHULTRAEGER_ID, beispielAnsprechpartner.id)

    expect(removeSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, beispielAnsprechpartner.id, expect.any(Function))
    expect(listSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, expect.any(Function))
  })
})
