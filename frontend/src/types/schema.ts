export type SchemaStatus =
  'GEPLANT' | 'VORHANDEN' | 'AKTIV' | 'DEAKTIVIERT' | 'MIGRATION_ERFORDERLICH' | 'FEHLER' | 'ARCHIVIERT'

export type SchemaSource = 'MANUELL' | 'SYNCHRONISIERT'

/**
 * Startvorschläge für die Umgebungsauswahl (ADR-012). `umgebung` ist bewusst freier Text statt
 * eines geschlossenen Enums - Betreiber können weitere Werte eingeben (z. B. Abnahme, Migration,
 * Archiv, Demo). "PRODUKTIV" bleibt der einzige fachlich ausgezeichnete Wert (kein Namenssuffix,
 * höchstens ein aktives Schema je Schule).
 */
export const PRODUKTIV_UMGEBUNG = 'PRODUKTIV'
export const STANDARD_UMGEBUNGEN = ['PRODUKTIV', 'TEST', 'SCHULUNG']

export interface Schema {
  id: string
  schuleId: string
  instanzId: string
  schemaName: string
  umgebung: string
  status: SchemaStatus
  aktiv: boolean
  beschreibung: string | null
  source: SchemaSource
  lastSyncedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SchemaCreateFormData {
  instanzId: string
  schemaName: string
  umgebung: string
  beschreibung?: string
}

export interface SchemaUpdateFormData {
  instanzId: string
  schemaName: string
  umgebung: string
  beschreibung?: string
  status: SchemaStatus
  aktiv: boolean
}

export interface SchemaNamingSuggestion {
  schemaName: string | null
  belastbareSchulnummer: boolean
}
