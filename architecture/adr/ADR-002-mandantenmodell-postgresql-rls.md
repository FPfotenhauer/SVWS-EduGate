# ADR-002: Mandantenmodell mit PostgreSQL Row-Level Security

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Die Mandantenhierarchie lautet: **Dienstleister → Schulträger → Schule → Schema**. Ein Schulträger kann mehrere hundert Schulen umfassen; jede Schule besitzt ein oder mehrere MariaDB-Schemas (Produktiv, Test) auf einer SVWS-Instanz (siehe [ADR-011](./ADR-011-svws-instanz-als-geteilte-betriebsressource.md): mandantenübergreifende Betriebsressource, nicht exklusiv einem Schulträger zugeordnet). Mandantentrennung ist Qualitätsziel Nr. 2: Kein Request darf Daten eines fremden Schulträgers liefern – auch nicht bei einem Programmierfehler in einer Query.

## Betrachtete Optionen

1. **Datenbank pro Mandant:** stärkste Trennung, aber operativ unverhältnismäßig (Migrationen, Backups, Verbindungs-Pools × Anzahl Schulträger).
2. **Schema pro Mandant in PostgreSQL:** Mittelweg, verkompliziert aber Cross-Tenant-Sichten des Dienstleisters (dessen Kernaufgabe).
3. **Shared Tables mit `tenant_id` + Row-Level Security (RLS):** eine Datenbank, `tenant_id` (= Schulträger-ID) auf jeder mandantenbezogenen Tabelle, RLS-Policies als zweite Verteidigungslinie unterhalb der Applikation.

## Entscheidung

Gewählt wurde **Option 3**. Konkret:

- Jede mandantenbezogene Tabelle trägt `tenant_id UUID NOT NULL REFERENCES schultraeger(id)`.
- RLS-Policies erzwingen `tenant_id = current_setting('edugate.tenant_id')::uuid`; die Applikation setzt den Tenant-Kontext pro Request/Transaktion aus dem validierten Token – niemals aus Client-Parametern allein.
- Der Dienstleister-Admin arbeitet über eine explizite Operator-Rolle (`edugate_operator`), deren Nutzung auditiert wird. *(Präzisiert durch [ADR-009](./ADR-009-operator-zugriff-und-audit.md): kein `BYPASSRLS`, sondern explizite Operator-Policies je Tabelle plus Pflicht-Audit.)*
- Der Gateway-DB-User ist zusätzlich auf `SELECT` der benötigten Tabellen beschränkt (siehe ADR-001).
- Ein automatisierter Test prüft bei jeder Migration, dass alle Tenant-Tabellen RLS aktiviert haben. *(Prüfregeln inkl. Wurzeltabelle `schultraeger` präzisiert in [ADR-008](./ADR-008-rls-wurzeltabelle-schultraeger.md).)*

## Konsequenzen

### Positiv

- Defense in Depth: Selbst eine fehlerhafte Query ohne `WHERE tenant_id = …` liefert keine fremden Daten.
- Cross-Tenant-Übersichten für den Dienstleister bleiben einfach (eine Datenbank).
- Skaliert problemlos auf hunderte Schulen pro Schulträger (Indizes auf `(tenant_id, …)`).

### Negativ / Risiken

- RLS-Policies müssen bei jeder neuen Tabelle mitgedacht werden → Checkliste + Test (s. o.).
- Verbindungs-Pooling erfordert sauberes Setzen/Zurücksetzen des Tenant-Kontexts (`SET LOCAL`).

## Nachtrag (SVWS-Serververwaltung): Ausnahme für `svws_instanz`

Bei der Umsetzung der SVWS-Serververwaltung stellte sich heraus, dass `svws_instanz` in der
V1-Migration fälschlich als gewöhnliche mandantenbezogene Fachtabelle mit `tenant_id` modelliert
worden war. Eine SVWS-Instanz ist jedoch eine **technische Betriebsressource des
Dienstleisters**, die mehreren Schulträgern gleichzeitig dienen kann – keine 1:1- oder
1:n-Bindung an genau einen Schulträger. Die Mandantenzuordnung entsteht ausschließlich über
`schema.tenant_id`/`schema.schule_id`/`schema.instanz_id`, nicht über eine `tenant_id` auf der
Instanz selbst.

**Entscheidung:** `svws_instanz` ist von der generischen Regel „jede mandantenbezogene Tabelle
trägt `tenant_id` + Tenant-RLS-Policy" ausgenommen. Sie erhält keine `tenant_id`-Spalte, bleibt
aber mit RLS `ENABLE`+`FORCE` abgesichert, allerdings mit einem eigenen Policy-Muster
(Operator-Vollzugriff, mandantenübergreifende Gateway-Read-Policy, keine Policy für die
tenant-gebundene Standardrolle). Details, Begründung und die präzisierte Prüfregel für den
RLS-Wächter-Test stehen in [ADR-011](./ADR-011-svws-instanz-als-geteilte-betriebsressource.md).
Diese Tabelle bleibt (unverändert seit V1) `schule` und `schema` als tatsächlich
mandantengebundene Fachtabellen im Sinne dieses ADRs.

Die einleitende Formulierung im „Kontext und Problemstellung“ oben ("…auf einer SVWS-Instanz")
wurde entsprechend korrigiert; sie beschrieb ursprünglich eine SVWS-Instanz fälschlich als dem
Schulträger zugehörig.

## Verweise

- ADR-001 (getrennte DB-User), ADR-008 (RLS der Wurzeltabelle), ADR-009 (Operator-Zugriff und Audit), ADR-011 (Ausnahme `svws_instanz` als geteilte Betriebsressource), ARCHITECTURE.md Kap. 5 (ER-Modell)
