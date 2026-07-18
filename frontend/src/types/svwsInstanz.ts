export type InstanzStatus = 'OK' | 'DEGRADED' | 'UNREACHABLE'

export interface SvwsInstanz {
  id: string
  name: string
  baseUrl: string
  beschreibung: string | null
  status: InstanzStatus
  aktiv: boolean
  credentialsHinterlegt: boolean
  credentialsUpdatedAt: string | null
  lastConnectionTestAt: string | null
  lastConnectionTestSuccess: boolean | null
  lastConnectionTestMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface SvwsInstanzPage {
  items: SvwsInstanz[]
  page: number
  size: number
  totalElements: number
}

export interface SvwsInstanzCreateFormData {
  name: string
  baseUrl: string
  beschreibung?: string
}

export interface SvwsInstanzUpdateFormData {
  name: string
  baseUrl: string
  beschreibung?: string
  status: InstanzStatus
  aktiv: boolean
}

export interface SvwsInstanzCredentialsFormData {
  username: string
  password: string
}
