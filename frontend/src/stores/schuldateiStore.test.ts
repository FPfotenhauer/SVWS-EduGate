import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchuldateiStore } from './schuldateiStore'
import * as schuldateiApi from '@/api/schuldateiApi'
import { ApiError } from '@/types/problem'
import type { SchuldateiImportStatus, SchuleKatalog, SchultraegerKatalog } from '@/types/schuldatei'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const beispielStatus: SchuldateiImportStatus = {
  id: '11111111-1111-1111-1111-111111111111',
  gestartetAm: '2026-01-01T00:00:00Z',
  beendetAm: '2026-01-01T00:00:05Z',
  erfolgreich: true,
  fehlermeldung: null,
  anzahlSchulen: 5703,
  anzahlSchultraeger: 1426,
  ausgeloestVon: 'admin@edugate.local',
}

const beispielSchule: SchuleKatalog = {
  id: '22222222-2222-2222-2222-222222222222',
  bundeslandkennung: 'NRW',
  schulnummer: '123456',
  schulname: 'Musterschule',
  schultraegernummer: '10100',
  schultraegername: 'Musterträger',
  schulform: '25',
  strasse: null,
  plz: null,
  ort: 'Bochum',
  kreis: null,
  telefon: null,
  fax: null,
  email: null,
  homepage: null,
  aufloesung: null,
  aktiv: true,
  lastSeenAt: '2026-01-01T00:00:00Z',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const beispielTraeger: SchultraegerKatalog = {
  id: '33333333-3333-3333-3333-333333333333',
  bundeslandkennung: 'NRW',
  traegernummer: '10100',
  traegername: 'Musterträger',
  traegerschaftsart: 'kreisfreie Stadt',
  strasse: null,
  plz: null,
  ort: 'Bochum',
  aufloesung: null,
  aktiv: true,
  lastSeenAt: '2026-01-01T00:00:00Z',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('schuldateiStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchStatus befüllt status bei Erfolg', async () => {
    vi.spyOn(schuldateiApi, 'getSchuldateiStatus').mockResolvedValue(beispielStatus)

    const store = useSchuldateiStore()
    await store.fetchStatus()

    expect(store.status).toEqual(beispielStatus)
    expect(store.statusLoading).toBe(false)
    expect(store.statusError).toBeNull()
  })

  it('fetchStatus lässt status null, solange noch kein Import gelaufen ist', async () => {
    vi.spyOn(schuldateiApi, 'getSchuldateiStatus').mockResolvedValue(null)

    const store = useSchuldateiStore()
    await store.fetchStatus()

    expect(store.status).toBeNull()
    expect(store.statusError).toBeNull()
  })

  it('fetchStatus setzt statusError bei einem ApiError', async () => {
    vi.spyOn(schuldateiApi, 'getSchuldateiStatus').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchuldateiStore()
    await store.fetchStatus()

    expect(store.statusError).toContain('kaputt')
  })

  it('refresh ruft die API auf und aktualisiert status', async () => {
    const refreshSpy = vi.spyOn(schuldateiApi, 'refreshSchuldatei').mockResolvedValue(beispielStatus)

    const store = useSchuldateiStore()
    const result = await store.refresh()

    expect(refreshSpy).toHaveBeenCalledWith(expect.any(Function))
    expect(result).toEqual(beispielStatus)
    expect(store.status).toEqual(beispielStatus)
    expect(store.refreshing).toBe(false)
    expect(store.refreshError).toBeNull()
  })

  it('refresh wirft den Fehler weiter und setzt refreshError', async () => {
    vi.spyOn(schuldateiApi, 'refreshSchuldatei').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'Netzwerkfehler',
        instance: null,
      }),
    )

    const store = useSchuldateiStore()
    await expect(store.refresh()).rejects.toBeInstanceOf(ApiError)

    expect(store.refreshError).toContain('Netzwerkfehler')
    expect(store.refreshing).toBe(false)
  })

  it('fetchSchulen befüllt schulen, page, size und totalElements', async () => {
    const listSpy = vi.spyOn(schuldateiApi, 'listSchuleKatalog').mockResolvedValue({
      items: [beispielSchule],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSchuldateiStore()
    await store.fetchSchulen({ q: 'Muster' })

    expect(listSpy).toHaveBeenCalledWith(
      { page: 0, size: 25, q: 'Muster', schultraegernummer: undefined, nurAktive: undefined },
      expect.any(Function),
    )
    expect(store.schulen).toEqual([beispielSchule])
    expect(store.schulenTotalElements).toBe(1)
    expect(store.schulenLoading).toBe(false)
  })

  it('fetchSchulen setzt schulenError bei einem ApiError, ohne die bisherige Liste zu verlieren', async () => {
    vi.spyOn(schuldateiApi, 'listSchuleKatalog').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchuldateiStore()
    await store.fetchSchulen()

    expect(store.schulenError).toContain('kaputt')
    expect(store.schulenLoading).toBe(false)
  })

  it('fetchSchultraeger befüllt schultraeger, page, size und totalElements', async () => {
    const listSpy = vi.spyOn(schuldateiApi, 'listSchultraegerKatalog').mockResolvedValue({
      items: [beispielTraeger],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSchuldateiStore()
    await store.fetchSchultraeger({ nurAktive: true })

    expect(listSpy).toHaveBeenCalledWith({ page: 0, size: 25, q: undefined, nurAktive: true }, expect.any(Function))
    expect(store.schultraeger).toEqual([beispielTraeger])
    expect(store.schultraegerTotalElements).toBe(1)
  })

  it('fetchSchulen übernimmt die Seite für den nächsten Aufruf', async () => {
    const listSpy = vi
      .spyOn(schuldateiApi, 'listSchuleKatalog')
      .mockResolvedValue({ items: [], page: 2, size: 25, totalElements: 0 })

    const store = useSchuldateiStore()
    await store.fetchSchulen({ page: 2 })

    expect(listSpy).toHaveBeenCalledWith(
      { page: 2, size: 25, q: undefined, schultraegernummer: undefined, nurAktive: undefined },
      expect.any(Function),
    )
  })
})
