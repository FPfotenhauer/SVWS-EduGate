export interface Ansprechpartner {
  id: string
  name: string
  vorname: string
  titel: string | null
  abteilung: string | null
  funktion: string | null
  email: string | null
  telefonFestnetz: string | null
  telefonMobil: string | null
  beschreibung: string | null
  createdAt: string
  updatedAt: string
}

export interface AnsprechpartnerFormData {
  name: string
  vorname: string
  titel?: string
  abteilung?: string
  funktion?: string
  email?: string
  telefonFestnetz?: string
  telefonMobil?: string
  beschreibung?: string
}
