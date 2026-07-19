import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'
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
})
