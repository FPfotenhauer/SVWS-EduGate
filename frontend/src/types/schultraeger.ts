export interface Schultraeger {
  id: string
  name: string
  traegernummer: string
  strasse: string | null
  plz: string | null
  ort: string | null
  beschreibung: string | null
  aktiv: boolean
  createdAt: string
  updatedAt: string
}

export interface SchultraegerPage {
  items: Schultraeger[]
  page: number
  size: number
  totalElements: number
}

export interface SchultraegerFormData {
  name: string
  traegernummer: string
  strasse?: string
  plz?: string
  ort?: string
  beschreibung?: string
}
