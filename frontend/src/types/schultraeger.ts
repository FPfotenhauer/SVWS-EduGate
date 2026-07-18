export interface Schultraeger {
  id: string
  name: string
  traegernummer: string
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
}
