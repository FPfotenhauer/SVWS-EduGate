import { InMemoryWebStorage, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

// Entscheidung: oidc-client-ts (statt keycloak-js) für den Authorization-Code+PKCE-Flow, weil es
// eine schmale, framework-unabhängige Implementierung des OIDC-Standardflows ist, ohne
// Keycloak-spezifische Zusatz-API - reicht für unseren Anmelde-/Token-Bedarf vollständig aus.
//
// Sicherheitslinie: Der Access-Token darf niemals in localStorage landen (persistiert über
// Tabs/Neustarts hinweg und wäre für jedes XSS auf dieser Origin lesbar). InMemoryWebStorage
// hält den State ausschließlich im JS-Heap; ein Reload verlangt daher eine neue Anmeldung.
export const oidcSettings: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTH_SERVER_URL,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID,
  redirect_uri: `${window.location.origin}/auth/callback`,
  silent_redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  automaticSilentRenew: true,
}
