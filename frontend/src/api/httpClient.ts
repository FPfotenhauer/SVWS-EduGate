import { ApiError, type ProblemDetail } from '@/types/problem'

const BASE_URL = import.meta.env.VITE_CONTROL_PLANE_API_URL
let unauthorizedRecoveryHandler: (() => Promise<boolean>) | null = null

export interface AccessTokenProvider {
  (): string | null
}

export function configureUnauthorizedRecoveryHandler(handler: (() => Promise<boolean>) | null): void {
  unauthorizedRecoveryHandler = handler
}

/**
 * Schmaler fetch-Wrapper für die Control-Plane-API: hängt das Bearer-Token an, dekodiert
 * RFC-7807-Fehlerantworten in {@link ApiError} und wirft ansonsten die geparste JSON-Antwort.
 */
export async function apiRequest<T>(
  path: string,
  options: { method?: string; body?: unknown; getAccessToken: AccessTokenProvider },
): Promise<T> {
  const execute = async (): Promise<Response> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    const token = options.getAccessToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    return fetch(`${BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  }

  let response = await execute()

  if (response.status === 401 && unauthorizedRecoveryHandler) {
    const recovered = await unauthorizedRecoveryHandler()
    if (recovered) {
      response = await execute()
    }
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  const payload = contentType.includes('json') ? await response.json() : undefined

  if (!response.ok) {
    const problem = contentType.includes('problem+json') ? (payload as ProblemDetail) : null
    throw new ApiError(response.status, problem)
  }

  return payload as T
}
