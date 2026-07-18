export type InstanzStatus = 'OK' | 'DEGRADED' | 'UNREACHABLE'

export interface SvwsInstanz {
  id: string
  name: string
  baseUrl: string
  status: InstanzStatus
  aktiv: boolean
  credentialsHinterlegt: boolean
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
}

export interface SvwsInstanzUpdateFormData {
  name: string
  baseUrl: string
  status: InstanzStatus
  aktiv: boolean
}

export interface SvwsInstanzCredentialsFormData {
  username: string
  password: string
}
