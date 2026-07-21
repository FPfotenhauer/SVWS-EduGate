/** Herkunft eines Schulträgers (ADR-020 "Schritt 2"). */
export type SchultraegerQuelle = 'LANDESLISTE' | 'MANUELL' | 'SONDERFALL'

export interface Schultraeger {
  id: string
  name: string
  traegernummer: string
  strasse: string | null
  plz: string | null
  ort: string | null
  beschreibung: string | null
  aktiv: boolean
  katalogId: string | null
  quelle: SchultraegerQuelle
  sonderfallHinweis: string | null
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

/**
 * Formulardaten beim Neuanlegen (ADR-020 "Schritt 2"): katalogId/sonderfall/sonderfallHinweis
 * gibt es nur hier, nicht beim Bearbeiten - die Herkunft eines Schulträgers wird beim Anlegen
 * einmalig festgelegt und bleibt danach unverändert (siehe SchultraegerService.update()).
 */
export interface SchultraegerCreateFormData extends SchultraegerFormData {
  katalogId?: string
  sonderfall?: boolean
  sonderfallHinweis?: string
}
