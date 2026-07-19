export interface SchemaUmgebung {
  id: string
  name: string
  system: boolean
  beschreibung: string | null
  aktiv: boolean
  createdAt: string
  updatedAt: string
}

export interface SchemaUmgebungCreateFormData {
  name: string
  beschreibung?: string
}

export interface SchemaUmgebungUpdateFormData {
  name: string
  beschreibung?: string
  aktiv: boolean
}
