/** Landes-Schuldatei-Referenzkatalog (ADR-020) - rein referenzielle Stammdaten, kein EduGate-Betreiberbestand. */
export interface SchuleKatalog {
  id: string
  bundeslandkennung: string
  schulnummer: string
  schulname: string
  schultraegernummer: string | null
  schultraegername: string | null
  schulform: string | null
  strasse: string | null
  plz: string | null
  ort: string | null
  kreis: string | null
  telefon: string | null
  fax: string | null
  email: string | null
  homepage: string | null
  aufloesung: string | null
  aktiv: boolean
  lastSeenAt: string
  createdAt: string
  updatedAt: string
}

export interface SchultraegerKatalog {
  id: string
  bundeslandkennung: string
  traegernummer: string
  traegername: string
  traegerschaftsart: string | null
  strasse: string | null
  plz: string | null
  ort: string | null
  aufloesung: string | null
  aktiv: boolean
  lastSeenAt: string
  createdAt: string
  updatedAt: string
}

export interface SchuleKatalogPage {
  items: SchuleKatalog[]
  page: number
  size: number
  totalElements: number
}

export interface SchultraegerKatalogPage {
  items: SchultraegerKatalog[]
  page: number
  size: number
  totalElements: number
}

export interface SchuldateiImportStatus {
  id: string
  gestartetAm: string
  beendetAm: string | null
  erfolgreich: boolean
  fehlermeldung: string | null
  anzahlSchulen: number | null
  anzahlSchultraeger: number | null
  ausgeloestVon: string
}
