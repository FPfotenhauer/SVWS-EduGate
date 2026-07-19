export interface Schule {
  id: string
  schultraegerId: string
  schulnummer: string
  name: string
  aktiv: boolean
  createdAt: string
  updatedAt: string
}

export interface SchuleFormData {
  schulnummer: string
  name: string
}
