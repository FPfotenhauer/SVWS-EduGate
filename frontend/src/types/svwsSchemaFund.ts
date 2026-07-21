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
