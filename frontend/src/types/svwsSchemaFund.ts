export type SchemaFundZuordnungsStatus = 'BEKANNT' | 'UNZUGEORDNET' | 'KONFLIKT'

export interface SvwsSchemaFund {
  id: string
  instanzId: string
  schemaName: string
  username: string
  isSvws: boolean | null
  revision: number | null
  isTainted: boolean | null
  isInConfig: boolean | null
  isDeactivated: boolean | null
  firstSeenAt: string
  lastSeenAt: string
  zuordnungsStatus: SchemaFundZuordnungsStatus
  schemaId: string | null
  schuleId: string | null
  schultraegerId: string | null
}

export interface SvwsSchemaSyncResult {
  success: boolean
  message: string
  gefundeneSchemata: number
  syncedAt: string
}

/** Schulstammdaten aus der SVWS-Privileged-API (ADR-014 Schritt 2) - rein informativ, niemals Grundlage einer automatischen Zuordnung. */
export interface SvwsSchulInfo {
  schulnummer: number | null
  schulform: string | null
  bezeichnung: string | null
  strassenname: string | null
  hausnummer: string | null
  hausnummerZusatz: string | null
  plz: string | null
  ort: string | null
}

export interface SvwsSchulInfoResult {
  success: boolean
  message: string
  schulInfo: SvwsSchulInfo | null
}

export interface SchemaFundZuordnungFormData {
  schultraegerId: string
  schuleId: string
  umgebung: string
  beschreibung?: string
}
