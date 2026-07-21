export type SchemaStatus =
  'GEPLANT' | 'VORHANDEN' | 'AKTIV' | 'DEAKTIVIERT' | 'MIGRATION_ERFORDERLICH' | 'FEHLER' | 'ARCHIVIERT'

export type SchemaSource = 'MANUELL' | 'SYNCHRONISIERT'

// `umgebung` ist bewusst freier Text statt eines geschlossenen Enums (ADR-012). Die zulässigen
// Werte werden nicht mehr als Frontend-Konstante gepflegt, sondern über die Betreiber-Einstellung
// "Umgebungen verwalten" (schemaUmgebungStore, Tabelle schema_umgebung). "PRODUKTIV" bleibt dort
// als Systemeintrag der einzige fachlich ausgezeichnete Wert (kein Namenssuffix, höchstens ein
// aktives Schema je Schule).

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

/**
 * Ergebnis eines Löschversuchs eines echten Schemas über die SVWS-Privileged-API. Ein
 * fachlicher Fehlschlag (fehlende Zugangsdaten, SVWS-seitige Ablehnung, Netzwerkfehler) ist kein
 * HTTP-Fehler - die Anfrage liefert 200 mit success=false und einer erklärenden message.
 */
export interface SchemaDestroyResult {
  success: boolean
  message: string | null
}
