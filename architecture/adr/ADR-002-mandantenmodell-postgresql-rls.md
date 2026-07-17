# ADR-002: Mandantenmodell mit PostgreSQL Row-Level Security

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Die Mandantenhierarchie lautet: **Dienstleister → Schulträger → Schule → Schema**. Ein Schulträger kann mehrere hundert Schulen umfassen; jede Schule besitzt ein oder mehrere MariaDB-Schemas (Produktiv, Test) auf einer SVWS-Instanz des Schulträgers. Mandantentrennung ist Qualitätsziel Nr. 2: Kein Request darf Daten eines fremden Schulträgers liefern – auch nicht bei einem Programmierfehler in einer Query.

## Betrachtete Optionen

1. **Datenbank pro Mandant:** stärkste Trennung, aber operativ unverhältnismäßig (Migrationen, Backups, Verbindungs-Pools × Anzahl Schulträger).
2. **Schema pro Mandant in PostgreSQL:** Mittelweg, verkompliziert aber Cross-Tenant-Sichten des Dienstleisters (dessen Kernaufgabe).
3. **Shared Tables mit `tenant_id` + Row-Level Security (RLS):** eine Datenbank, `tenant_id` (= Schulträger-ID) auf jeder mandantenbezogenen Tabelle, RLS-Policies als zweite Verteidigungslinie unterhalb der Applikation.

## Entscheidung

Gewählt wurde **Option 3**. Konkret:

- Jede mandantenbezogene Tabelle trägt `tenant_id UUID NOT NULL REFERENCES schultraeger(id)`.
- RLS-Policies erzwingen `tenant_id = current_setting('edugate.tenant_id')::uuid`; die Applikation setzt den Tenant-Kontext pro Request/Transaktion aus dem validierten Token – niemals aus Client-Parametern allein.
- Der Dienstleister-Admin arbeitet über eine explizite Bypass-Rolle (`edugate_operator`), deren Nutzung auditiert wird.
- Der Gateway-DB-User ist zusätzlich auf `SELECT` der benötigten Tabellen beschränkt (siehe ADR-001).
- Ein automatisierter Test prüft bei jeder Migration, dass alle Tenant-Tabellen RLS aktiviert haben.

## Konsequenzen

### Positiv

- Defense in Depth: Selbst eine fehlerhafte Query ohne `WHERE tenant_id = …` liefert keine fremden Daten.
- Cross-Tenant-Übersichten für den Dienstleister bleiben einfach (eine Datenbank).
- Skaliert problemlos auf hunderte Schulen pro Schulträger (Indizes auf `(tenant_id, …)`).

### Negativ / Risiken

- RLS-Policies müssen bei jeder neuen Tabelle mitgedacht werden → Checkliste + Test (s. o.).
- Verbindungs-Pooling erfordert sauberes Setzen/Zurücksetzen des Tenant-Kontexts (`SET LOCAL`).

## Verweise

- ADR-001 (getrennte DB-User), ARCHITECTURE.md Kap. 5 (ER-Modell)
