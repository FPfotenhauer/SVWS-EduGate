import { UserManager, type User } from 'oidc-client-ts'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { oidcSettings } from './oidcConfig'

export const userManager = new UserManager(oidcSettings)

/** Hält den angemeldeten Benutzer nur im Speicher (Pinia-State), niemals in localStorage. */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => user.value !== null && !user.value.expired)
  const accessToken = computed(() => user.value?.access_token ?? null)
  const displayName = computed(
    () => (user.value?.profile.preferred_username as string | undefined) ?? user.value?.profile.sub ?? null,
  )
  const roles = computed<string[]>(() => {
    const realmAccess = user.value?.profile.realm_access as { roles?: string[] } | undefined
    return realmAccess?.roles ?? []
  })

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

  return { user, isAuthenticated, accessToken, displayName, roles, login, logout, completeLogin, restore }
})
