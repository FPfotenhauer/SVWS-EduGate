import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, configureUnauthorizedRecoveryHandler } from './httpClient'
import { ApiError } from '@/types/problem'

function jsonResponse(body: unknown, status: number, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': contentType } })
}

describe('apiRequest', () => {
  afterEach(() => {
    configureUnauthorizedRecoveryHandler(null)
    vi.unstubAllGlobals()
  })

  it('hängt das Bearer-Token als Authorization-Header an, wenn eines vorhanden ist', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }, 200))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/schultraeger', { getAccessToken: () => 'mein-token' })

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/schultraeger'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer mein-token' }) }),
    )

  })

  it('hängt keinen Authorization-Header an, wenn kein Token vorhanden ist', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }, 200))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/schultraeger', { getAccessToken: () => null })

    const [, requestInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((requestInit.headers as Record<string, string>).Authorization).toBeUndefined()

  })

  it('liefert den geparsten JSON-Body bei Erfolg', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ name: 'Musterstadt' }, 200)))

    const result = await apiRequest<{ name: string }>('/schultraeger/1', { getAccessToken: () => null })

    expect(result).toEqual({ name: 'Musterstadt' })
  })

  it('liefert undefined bei 204 No Content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    const result = await apiRequest('/schultraeger/1', { method: 'DELETE', getAccessToken: () => null })

    expect(result).toBeUndefined()
  })

  it('wirft ApiError mit dem geparsten ProblemDetail bei einer Fehlerantwort', async () => {
    const problem = {
      type: 'urn:problem-type:not-found',
      title: 'Nicht gefunden',
      status: 404,
      detail: 'weg',
      instance: null,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 404, 'application/problem+json')))

    await expect(apiRequest('/schultraeger/1', { getAccessToken: () => null })).rejects.toMatchObject({
      status: 404,
      problem,
    })

  })

  it('ApiError ist eine echte Instanz von ApiError', async () => {
    const problem = { type: 'urn:problem-type:conflict', title: 'Konflikt', status: 409, detail: null, instance: null }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 409, 'application/problem+json')))

    await expect(apiRequest('/schultraeger', { getAccessToken: () => null })).rejects.toBeInstanceOf(ApiError)

  })

  it('erneuert bei 401 einmal und wiederholt den Request erfolgreich', async () => {
    const tokenProvider = vi.fn().mockReturnValueOnce('altes-token').mockReturnValueOnce('neues-token')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401, headers: { 'www-authenticate': 'Bearer' } }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }, 200))
    const recoveryMock = vi.fn().mockResolvedValue(true)

    vi.stubGlobal('fetch', fetchMock)
    configureUnauthorizedRecoveryHandler(recoveryMock)

    const result = await apiRequest<{ ok: boolean }>('/schultraeger', { getAccessToken: tokenProvider })

    expect(result).toEqual({ ok: true })
    expect(recoveryMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(tokenProvider).toHaveBeenCalledTimes(2)
  })

  it('wirft bei 401 einen ApiError, wenn Erneuerung nicht möglich ist', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401, headers: { 'www-authenticate': 'Bearer' } }))
    const recoveryMock = vi.fn().mockResolvedValue(false)

    vi.stubGlobal('fetch', fetchMock)
    configureUnauthorizedRecoveryHandler(recoveryMock)

    await expect(apiRequest('/schultraeger', { getAccessToken: () => 'token' })).rejects.toBeInstanceOf(ApiError)

    expect(recoveryMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
