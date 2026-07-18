import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSvwsInstanzStore } from './svwsInstanzStore'
import * as svwsInstanzApi from '@/api/svwsInstanzApi'
import { ApiError } from '@/types/problem'
import type { SvwsInstanz } from '@/types/svwsInstanz'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const beispielInstanz: SvwsInstanz = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Testinstanz',
  baseUrl: 'https://svws.example.org',
  beschreibung: null,
  status: 'OK',
  aktiv: true,
  credentialsHinterlegt: false,
  credentialsUpdatedAt: null,
  lastConnectionTestAt: null,
  lastConnectionTestSuccess: null,
  lastConnectionTestMessage: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('svwsInstanzStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items, page, size und totalElements bei Erfolg', async () => {
    vi.spyOn(svwsInstanzApi, 'listSvwsInstanzen').mockResolvedValue({
      items: [beispielInstanz],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSvwsInstanzStore()
    await store.fetchList()

    expect(store.items).toEqual([beispielInstanz])
    expect(store.totalElements).toBe(1)
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError, ohne die bisherige Liste zu verlieren', async () => {
    vi.spyOn(svwsInstanzApi, 'listSvwsInstanzen').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSvwsInstanzStore()
    await store.fetchList()

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit den Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(svwsInstanzApi, 'createSvwsInstanz').mockResolvedValue(beispielInstanz)
    const listSpy = vi
      .spyOn(svwsInstanzApi, 'listSvwsInstanzen')
      .mockResolvedValue({ items: [beispielInstanz], page: 0, size: 25, totalElements: 1 })

    const store = useSvwsInstanzStore()
    const result = await store.create({ name: 'Testinstanz', baseUrl: 'https://svws.example.org' })

    expect(createSpy).toHaveBeenCalledWith(
      { name: 'Testinstanz', baseUrl: 'https://svws.example.org' },
      expect.any(Function),
    )
    expect(listSpy).toHaveBeenCalled()
    expect(result).toEqual(beispielInstanz)
  })

  it('update ruft die API mit den Formulardaten auf und lädt die Liste danach neu', async () => {
    const updateSpy = vi.spyOn(svwsInstanzApi, 'updateSvwsInstanz').mockResolvedValue(beispielInstanz)
    const listSpy = vi
      .spyOn(svwsInstanzApi, 'listSvwsInstanzen')
      .mockResolvedValue({ items: [beispielInstanz], page: 0, size: 25, totalElements: 1 })

    const store = useSvwsInstanzStore()
    const result = await store.update(beispielInstanz.id, {
      name: 'Testinstanz',
      baseUrl: 'https://svws.example.org',
      status: 'DEGRADED',
      aktiv: true,
    })

    expect(updateSpy).toHaveBeenCalledWith(
      beispielInstanz.id,
      { name: 'Testinstanz', baseUrl: 'https://svws.example.org', status: 'DEGRADED', aktiv: true },
      expect.any(Function),
    )
    expect(listSpy).toHaveBeenCalled()
    expect(result).toEqual(beispielInstanz)
  })

  it('deactivate ruft die API mit der id auf und lädt die Liste danach neu', async () => {
    const deactivateSpy = vi.spyOn(svwsInstanzApi, 'deactivateSvwsInstanz').mockResolvedValue(undefined)
    vi.spyOn(svwsInstanzApi, 'listSvwsInstanzen').mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })

    const store = useSvwsInstanzStore()
    await store.deactivate(beispielInstanz.id)

    expect(deactivateSpy).toHaveBeenCalledWith(beispielInstanz.id, expect.any(Function))
  })

  it('setCredentials ruft die API mit den Zugangsdaten auf, ohne die Liste neu zu laden', async () => {
    const credentialsSpy = vi.spyOn(svwsInstanzApi, 'setSvwsInstanzCredentials').mockResolvedValue(undefined)
    const listSpy = vi.spyOn(svwsInstanzApi, 'listSvwsInstanzen')

    const store = useSvwsInstanzStore()
    await store.setCredentials(beispielInstanz.id, { username: 'svws-admin', password: 'geheim' })

    expect(credentialsSpy).toHaveBeenCalledWith(
      beispielInstanz.id,
      { username: 'svws-admin', password: 'geheim' },
      expect.any(Function),
    )
    expect(listSpy).not.toHaveBeenCalled()
  })

  it('setCredentials wirft den Fehler weiter, damit die Ansicht ihn anzeigen kann', async () => {
    vi.spyOn(svwsInstanzApi, 'setSvwsInstanzCredentials').mockRejectedValue(
      new ApiError(404, {
        type: 'urn:problem-type:not-found',
        title: 'Nicht gefunden',
        status: 404,
        detail: 'weg',
        instance: null,
      }),
    )

    const store = useSvwsInstanzStore()
    await expect(store.setCredentials(beispielInstanz.id, { username: 'a', password: 'b' })).rejects.toBeInstanceOf(
      ApiError,
    )
  })

  it('fetchList übernimmt Seiten- und Suchparameter für den nächsten Aufruf', async () => {
    const listSpy = vi
      .spyOn(svwsInstanzApi, 'listSvwsInstanzen')
      .mockResolvedValue({ items: [], page: 2, size: 25, totalElements: 0 })

    const store = useSvwsInstanzStore()
    await store.fetchList({ page: 2, q: 'svws.example.org' })

    expect(listSpy).toHaveBeenCalledWith({ page: 2, size: 25, q: 'svws.example.org' }, expect.any(Function))
  })

  it('fetchList übernimmt den Statusfilter für den nächsten Aufruf', async () => {
    const listSpy = vi
      .spyOn(svwsInstanzApi, 'listSvwsInstanzen')
      .mockResolvedValue({ items: [], page: 0, size: 25, totalElements: 0 })

    const store = useSvwsInstanzStore()
    await store.fetchList({ status: 'DEGRADED' })

    expect(listSpy).toHaveBeenCalledWith({ page: 0, size: 25, status: 'DEGRADED' }, expect.any(Function))
  })

  it('testConnection ruft die API auf und aktualisiert den Eintrag in items', async () => {
    const aktualisiert: SvwsInstanz = {
      ...beispielInstanz,
      status: 'OK',
      lastConnectionTestSuccess: true,
      lastConnectionTestAt: '2026-01-02T00:00:00Z',
      lastConnectionTestMessage: 'Verbindung erfolgreich (Status 200).',
    }
    const testSpy = vi.spyOn(svwsInstanzApi, 'testSvwsInstanzConnection').mockResolvedValue(aktualisiert)
    vi.spyOn(svwsInstanzApi, 'listSvwsInstanzen').mockResolvedValue({
      items: [beispielInstanz],
      page: 0,
      size: 25,
      totalElements: 1,
    })

    const store = useSvwsInstanzStore()
    await store.fetchList()
    const result = await store.testConnection(beispielInstanz.id)

    expect(testSpy).toHaveBeenCalledWith(beispielInstanz.id, expect.any(Function))
    expect(result).toEqual(aktualisiert)
    expect(store.items[0]).toEqual(aktualisiert)
  })
})
