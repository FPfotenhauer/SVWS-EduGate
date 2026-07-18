/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_OIDC_AUTH_SERVER_URL: string
  readonly VITE_OIDC_CLIENT_ID: string
  readonly VITE_CONTROL_PLANE_API_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
