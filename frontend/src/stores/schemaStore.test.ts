import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSchemaStore } from './schemaStore'
import * as schemaApi from '@/api/schemaApi'
import { ApiError } from '@/types/problem'
import type { Schema } from '@/types/schema'

vi.mock('@/auth/authStore', () => ({
  useAuthStore: () => ({ accessToken: 'test-token' }),
}))

const SCHULTRAEGER_ID = '11111111-1111-1111-1111-111111111111'
const SCHULE_ID = '22222222-2222-2222-2222-222222222222'

const beispielSchema: Schema = {
  id: '44444444-4444-4444-4444-444444444444',
  schuleId: SCHULE_ID,
  instanzId: '55555555-5555-5555-5555-555555555555',
  schemaName: '123456',
  umgebung: 'PRODUKTIV',
  status: 'GEPLANT',
  aktiv: true,
  beschreibung: null,
  source: 'MANUELL',
  lastSyncedAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('schemaStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('fetchList befüllt items bei Erfolg', async () => {
    vi.spyOn(schemaApi, 'listSchemata').mockResolvedValue([beispielSchema])

    const store = useSchemaStore()
    await store.fetchList(SCHULTRAEGER_ID, SCHULE_ID)

    expect(store.items).toEqual([beispielSchema])
    expect(store.loading).toBe(false)
    expect(store.errorMessage).toBeNull()
  })

  it('fetchList setzt errorMessage bei einem ApiError', async () => {
    vi.spyOn(schemaApi, 'listSchemata').mockRejectedValue(
      new ApiError(409, {
        type: 'urn:problem-type:conflict',
        title: 'Konflikt',
        status: 409,
        detail: 'Schema existiert bereits',
        instance: null,
      }),
    )

    const store = useSchemaStore()
    await store.fetchList(SCHULTRAEGER_ID, SCHULE_ID)

    expect(store.errorMessage).toContain('Schema existiert bereits')
    expect(store.loading).toBe(false)
  })

  it('create ruft die API mit Schulträger-/Schul-ID und Formulardaten auf und lädt die Liste danach neu', async () => {
    const createSpy = vi.spyOn(schemaApi, 'createSchema').mockResolvedValue(beispielSchema)
    const listSpy = vi.spyOn(schemaApi, 'listSchemata').mockResolvedValue([beispielSchema])

    const store = useSchemaStore()
    const formData = { instanzId: beispielSchema.instanzId, schemaName: '123456', umgebung: 'PRODUKTIV' }
    const result = await store.create(SCHULTRAEGER_ID, SCHULE_ID, formData)

    expect(createSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, SCHULE_ID, formData, expect.any(Function))
    expect(listSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, SCHULE_ID, expect.any(Function))
    expect(result).toEqual(beispielSchema)
  })

  it('update ruft die API mit Schulträger-/Schul-/Schema-ID und Formulardaten auf', async () => {
    const updateSpy = vi.spyOn(schemaApi, 'updateSchema').mockResolvedValue(beispielSchema)
    vi.spyOn(schemaApi, 'listSchemata').mockResolvedValue([beispielSchema])

    const store = useSchemaStore()
    const formData = {
      instanzId: beispielSchema.instanzId,
      schemaName: '123456',
      umgebung: 'PRODUKTIV',
      status: 'AKTIV' as const,
      aktiv: true,
    }
    await store.update(SCHULTRAEGER_ID, SCHULE_ID, beispielSchema.id, formData)

    expect(updateSpy).toHaveBeenCalledWith(
      SCHULTRAEGER_ID,
      SCHULE_ID,
      beispielSchema.id,
      formData,
      expect.any(Function),
    )
  })

  it('deactivate ruft die API auf und lädt die Liste danach neu', async () => {
    const deactivateSpy = vi.spyOn(schemaApi, 'deactivateSchema').mockResolvedValue(beispielSchema)
    const listSpy = vi.spyOn(schemaApi, 'listSchemata').mockResolvedValue([])

    const store = useSchemaStore()
    await store.deactivate(SCHULTRAEGER_ID, SCHULE_ID, beispielSchema.id)

    expect(deactivateSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, SCHULE_ID, beispielSchema.id, expect.any(Function))
    expect(listSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, SCHULE_ID, expect.any(Function))
  })

  it('namingSuggestion ruft die API mit Umgebung auf', async () => {
    const suggestionSpy = vi
      .spyOn(schemaApi, 'schemaNamingSuggestion')
      .mockResolvedValue({ schemaName: '123456', belastbareSchulnummer: true })

    const store = useSchemaStore()
    const result = await store.namingSuggestion(SCHULTRAEGER_ID, SCHULE_ID, 'PRODUKTIV')

    expect(suggestionSpy).toHaveBeenCalledWith(SCHULTRAEGER_ID, SCHULE_ID, 'PRODUKTIV', expect.any(Function))
    expect(result).toEqual({ schemaName: '123456', belastbareSchulnummer: true })
  })
})
