/** Herkunft einer Schule (ADR-020). */
export type SchuleQuelle = 'LANDESLISTE' | 'MANUELL' | 'SONDERFALL'

export interface Schule {
  id: string
  schultraegerId: string
  schulnummer: string
  name: string
  aktiv: boolean
  katalogId: string | null
  quelle: SchuleQuelle
  sonderfallHinweis: string | null
  createdAt: string
  updatedAt: string
}

export interface SchuleFormData {
  schulnummer: string
  name: string
}

/**
 * Formulardaten beim Neuanlegen (ADR-020): katalogId/sonderfall/sonderfallHinweis gibt es nur
 * hier, nicht beim Bearbeiten - die Herkunft einer Schule wird beim Anlegen einmalig festgelegt
 * und bleibt danach unverändert (analog SchultraegerCreateFormData).
 */
export interface SchuleCreateFormData extends SchuleFormData {
  katalogId?: string
  sonderfall?: boolean
  sonderfallHinweis?: string
}
