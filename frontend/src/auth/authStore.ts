import { UserManager, type User } from 'oidc-client-ts'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { oidcSettings } from './oidcConfig'

export const userManager = new UserManager(oidcSettings)
let renewTokenInFlight: Promise<boolean> | null = null

/** Hält den angemeldeten Benutzer nur im Speicher (Pinia-State), niemals in localStorage. */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => user.value !== null && !user.value.expired)
  const accessToken = computed(() => user.value?.access_token ?? null)
  const displayName = computed(
    () => (user.value?.profile.preferred_username as string | undefined) ?? user.value?.profile.sub ?? null,
  )
  // Rollen stehen laut Realm-Konfiguration (docker/keycloak/realm-edugate.json, Mapper
  // "realm roles") nur im Access-Token (access.token.claim), nicht im ID-Token - oidc-client-ts
  // befüllt `user.profile` aber aus dem ID-Token. Ein Blick in `profile.realm_access` liefert
  // daher immer eine leere Liste; die Rollen müssen aus dem Access-Token-JWT selbst gelesen
  // werden (nur zur UI-Anzeige, keine Signaturprüfung - die eigentliche Durchsetzung bleibt beim
  // Backend, das den Access-Token ohnehin gegen Keycloaks Public Key validiert).
  const roles = computed<string[]>(() => decodeRealmRolesFromAccessToken(user.value?.access_token))

  async function login(): Promise<void> {
    await userManager.signinRedirect()
  }

  async function logout(): Promise<void> {
    await userManager.signoutRedirect()
    user.value = null
  }

  async function completeLogin(): Promise<void> {
    user.value = await userManager.signinRedirectCallback()
  }

  async function restore(): Promise<void> {
    user.value = await userManager.getUser()
  }

  async function renewToken(): Promise<boolean> {
    if (renewTokenInFlight) {
      return renewTokenInFlight
    }

    renewTokenInFlight = (async () => {
      try {
        const currentUser = await userManager.getUser()
        user.value = currentUser

        if (!currentUser) {
          return false
        }

        if (!currentUser.expired) {
          return true
        }

        const renewedUser = await userManager.signinSilent()
        user.value = renewedUser
        return !renewedUser.expired
      } catch {
        // Wenn die stille Erneuerung scheitert (z. B. kein gültiger Session-Cookie mehr),
        // darf kein veralteter User im Store verbleiben.
        user.value = null
        return false
      } finally {
        renewTokenInFlight = null
      }
    })()

    return renewTokenInFlight
  }

  return { user, isAuthenticated, accessToken, displayName, roles, login, logout, completeLogin, restore, renewToken }
})

/**
 * Liest `realm_access.roles` aus dem (unverifizierten) Payload eines JWT-Access-Tokens. Nur für
 * die UI-Anzeige (z. B. Sichtbarkeit des Zahnrad-Icons) - keine Sicherheitsentscheidung, die
 * bleibt beim Backend.
 */
function decodeRealmRolesFromAccessToken(accessToken: string | undefined): string[] {
  const payload = accessToken?.split('.')[1]
  if (!payload) {
    return []
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(base64)) as { realm_access?: { roles?: string[] } }
    return claims.realm_access?.roles ?? []
  } catch {
    return []
  }
}
