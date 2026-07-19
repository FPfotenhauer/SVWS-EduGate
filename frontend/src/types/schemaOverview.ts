import type { InstanzStatus } from '@/types/svwsInstanz'
import type { SchemaSource, SchemaStatus } from '@/types/schema'

/**
 * Mandantenübergreifende Betreiber-Übersichtszeile einer Schuldatenbank (ADR-013): verdichtet
 * Schema mit Schule-, Schulträger- und Instanz-Kontext für die Instanz- und die fachliche
 * Schuldatenbank-Sicht.
 */
export interface SchemaOverviewItem {
  id: string
  schemaName: string
  umgebung: string
  status: SchemaStatus
  aktiv: boolean
  beschreibung: string | null
  source: SchemaSource
  lastSyncedAt: string | null
  createdAt: string
  updatedAt: string
  schuleId: string
  schulnummer: string
  schuleName: string
  schultraegerId: string
  schultraegerName: string
  instanzId: string
  instanzName: string
  instanzBaseUrl: string
  instanzStatus: InstanzStatus
}

export interface SchemaOverviewPage {
  items: SchemaOverviewItem[]
  page: number
  size: number
  totalElements: number
}

export interface SchemaOverviewFilter {
  page?: number
  instanzId?: string
  schultraegerId?: string
  umgebung?: string
  status?: SchemaStatus | ''
  q?: string
}
