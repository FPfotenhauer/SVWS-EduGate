import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore, userManager } from './authStore'
import type { User } from 'oidc-client-ts'

// Baut ein unsigniertes, aber strukturell korrektes JWT (header.payload.signature) mit den
// übergebenen Claims im Payload - reicht aus, um decodeRealmRolesFromAccessToken zu testen, ohne
// echte Keycloak-Tokens zu brauchen.
function fakeAccessToken(claims: Record<string, unknown>): string {
  const base64url = (value: object): string =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${base64url({ alg: 'none' })}.${base64url(claims)}.signature`
}

function alsUser(accessToken: string | undefined): User {
  return { access_token: accessToken, profile: {} } as unknown as User
}

describe('authStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('roles ist leer ohne angemeldeten Benutzer', () => {
    const store = useAuthStore()
    expect(store.roles).toEqual([])
  })

  it('roles liest realm_access.roles aus dem Access-Token, nicht aus profile', () => {
    const store = useAuthStore()
    store.user = alsUser(fakeAccessToken({ realm_access: { roles: ['dienstleister-admin'] } }))

    expect(store.roles).toEqual(['dienstleister-admin'])
  })

  it('roles ist leer, wenn der Access-Token kein realm_access enthält', () => {
    const store = useAuthStore()
    store.user = alsUser(fakeAccessToken({ sub: 'irgendwer' }))

    expect(store.roles).toEqual([])
  })

  it('roles ist leer bei einem fehlerhaften/nicht dekodierbaren Access-Token', () => {
    const store = useAuthStore()
    store.user = alsUser('kein.gueltiges-jwt')

    expect(store.roles).toEqual([])
  })

  it('renewToken erneuert ein abgelaufenes Token per signinSilent', async () => {
    const abgelaufen = { expired: true } as User
    const erneuert = { expired: false, access_token: 'neu' } as User
    vi.spyOn(userManager, 'getUser').mockResolvedValue(abgelaufen)
    vi.spyOn(userManager, 'signinSilent').mockResolvedValue(erneuert)

    const store = useAuthStore()
    const result = await store.renewToken()

    expect(result).toBe(true)
    expect(store.user).toStrictEqual(erneuert)
  })

  it('renewToken liefert false, wenn stille Erneuerung fehlschlägt', async () => {
    const abgelaufen = { expired: true } as User
    vi.spyOn(userManager, 'getUser').mockResolvedValue(abgelaufen)
    vi.spyOn(userManager, 'signinSilent').mockRejectedValue(new Error('silent renew failed'))

    const store = useAuthStore()
    const result = await store.renewToken()

    expect(result).toBe(false)
    expect(store.user).toBeNull()
  })
})
