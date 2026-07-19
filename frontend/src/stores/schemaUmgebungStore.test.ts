import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchemaUmgebungStore } from './schemaUmgebungStore'
import * as schemaUmgebungApi from '@/api/schemaUmgebungApi'
import { ApiError } from '@/types/problem'
import type { SchemaUmgebung } from '@/types/schemaUmgebung'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const beispielUmgebung: SchemaUmgebung = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'PRODUKTIV',
  system: true,
  beschreibung: 'Produktivbetrieb',
  aktiv: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('schemaUmgebungStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items bei Erfolg', async () => {
    vi.spyOn(schemaUmgebungApi, 'listSchemaUmgebungen').mockResolvedValue([beispielUmgebung])

    const store = useSchemaUmgebungStore()
    await store.fetchList()

    expect(store.items).toEqual([beispielUmgebung])
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError', async () => {
    vi.spyOn(schemaUmgebungApi, 'listSchemaUmgebungen').mockRejectedValue(
      new ApiError(500, {
        type: 'urn:problem-type:internal-error',
        title: 'Fehler',
        status: 500,
        detail: 'kaputt',
        instance: null,
      }),
    )

    const store = useSchemaUmgebungStore()
    await store.fetchList()

    expect(store.errorMessage).toContain('kaputt')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(schemaUmgebungApi, 'createSchemaUmgebung').mockResolvedValue(beispielUmgebung)
    const listSpy = vi.spyOn(schemaUmgebungApi, 'listSchemaUmgebungen').mockResolvedValue([beispielUmgebung])

    const store = useSchemaUmgebungStore()
    const result = await store.create({ name: 'PRODUKTIV', beschreibung: 'Produktivbetrieb' })

    expect(createSpy).toHaveBeenCalledWith(
      { name: 'PRODUKTIV', beschreibung: 'Produktivbetrieb' },
      expect.any(Function),
    )
    expect(listSpy).toHaveBeenCalledWith(expect.any(Function))
    expect(result).toEqual(beispielUmgebung)
  })

  it('update ruft die API mit ID und Formulardaten auf', async () => {
    const updateSpy = vi.spyOn(schemaUmgebungApi, 'updateSchemaUmgebung').mockResolvedValue(beispielUmgebung)
    vi.spyOn(schemaUmgebungApi, 'listSchemaUmgebungen').mockResolvedValue([beispielUmgebung])

    const store = useSchemaUmgebungStore()
    const formData = { name: 'PRODUKTIV', beschreibung: 'geändert', aktiv: true }
    await store.update(beispielUmgebung.id, formData)

    expect(updateSpy).toHaveBeenCalledWith(beispielUmgebung.id, formData, expect.any(Function))
  })

  it('deactivate ruft die API auf und lädt die Liste danach neu', async () => {
    const deactivateSpy = vi.spyOn(schemaUmgebungApi, 'deactivateSchemaUmgebung').mockResolvedValue({
      ...beispielUmgebung,
      aktiv: false,
    })
    const listSpy = vi.spyOn(schemaUmgebungApi, 'listSchemaUmgebungen').mockResolvedValue([])

    const store = useSchemaUmgebungStore()
    await store.deactivate(beispielUmgebung.id)

    expect(deactivateSpy).toHaveBeenCalledWith(beispielUmgebung.id, expect.any(Function))
    expect(listSpy).toHaveBeenCalledWith(expect.any(Function))
  })
})
