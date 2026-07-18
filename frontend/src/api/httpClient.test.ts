import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from './httpClient'
import { ApiError } from '@/types/problem'

function jsonResponse(body: unknown, status: number, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': contentType } })
}

describe('apiRequest', () => {
  it('hängt das Bearer-Token als Authorization-Header an, wenn eines vorhanden ist', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }, 200))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/schultraeger', { getAccessToken: () => 'mein-token' })

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/schultraeger'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer mein-token' }) }),
    )

    vi.unstubAllGlobals()
  })

  it('hängt keinen Authorization-Header an, wenn kein Token vorhanden ist', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }, 200))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/schultraeger', { getAccessToken: () => null })

    const [, requestInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((requestInit.headers as Record<string, string>).Authorization).toBeUndefined()

    vi.unstubAllGlobals()
  })

  it('liefert den geparsten JSON-Body bei Erfolg', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ name: 'Musterstadt' }, 200)))

    const result = await apiRequest<{ name: string }>('/schultraeger/1', { getAccessToken: () => null })

    expect(result).toEqual({ name: 'Musterstadt' })
    vi.unstubAllGlobals()
  })

  it('liefert undefined bei 204 No Content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    const result = await apiRequest('/schultraeger/1', { method: 'DELETE', getAccessToken: () => null })

    expect(result).toBeUndefined()
    vi.unstubAllGlobals()
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

    vi.unstubAllGlobals()
  })

  it('ApiError ist eine echte Instanz von ApiError', async () => {
    const problem = { type: 'urn:problem-type:conflict', title: 'Konflikt', status: 409, detail: null, instance: null }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(problem, 409, 'application/problem+json')))

    await expect(apiRequest('/schultraeger', { getAccessToken: () => null })).rejects.toBeInstanceOf(ApiError)

    vi.unstubAllGlobals()
  })
})
