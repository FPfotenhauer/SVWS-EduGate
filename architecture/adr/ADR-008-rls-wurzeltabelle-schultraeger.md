# ADR-008: RLS-Behandlung der Mandanten-Wurzeltabelle `schultraeger`

- **Status:** accepted
- **Datum:** 2026-07-17
- **Entscheider:** Franko Pfotenhauer
- **Anlass:** [Issue #2](https://github.com/FPfotenhauer/SVWS-EduGate/issues/2) (openpawde)

## Kontext und Problemstellung

ADR-002 fordert `tenant_id` plus Row-Level Security auf jeder mandantenbezogenen Tabelle. Die Tabelle `schultraeger` ist jedoch selbst die Wurzel des Mandantenmodells: Ihre `id` **ist** die Tenant-ID. Ohne explizite Regel wäre die Migration `V1__initial_schema.sql` an dieser Stelle mehrdeutig, und der RLS-Wächter-Test hätte keine eindeutige Prüfregel für die Wurzeltabelle.

## Betrachtete Optionen

1. **Keine RLS auf `schultraeger`;** Zugriffsbegrenzung nur über DB-Rollen und Services.
2. **RLS-Policy auf die eigene ID:** `id = current_setting('edugate.tenant_id')::uuid`.
3. **Redundante Spalte `tenant_id`** mit `tenant_id = id`, um ein einheitliches Spalten-/Testmuster zu erhalten.
4. Sonstige Sonder-Policies für Root-Tenant-Tabellen.

## Entscheidung

Gewählt wurde **Option 2**:

- `schultraeger` erhält **keine** `tenant_id`-Spalte.
- `schultraeger` erhält RLS mit `ENABLE` **und** `FORCE` und der Policy für tenant-gebundene Rollen:

  ```sql
  CREATE POLICY schultraeger_tenant ON schultraeger
      USING (id = current_setting('edugate.tenant_id')::uuid);
  ```

- Mandantenübergreifender Zugriff auf `schultraeger` (Dienstleister-Kontext) läuft ausschließlich über die expliziten Operator-Policies aus ADR-009 – nicht über eine Aufweichung dieser Policy.

**Präzisierte Prüfregeln für den RLS-Wächter-Test** (ersetzt die Formulierung „alle Tenant-Tabellen“ aus ADR-002 durch zwei eindeutige Regeln):

1. Jede Tabelle mit einer Spalte `tenant_id` hat RLS `ENABLE` + `FORCE` und mindestens eine Policy, die `tenant_id` gegen `current_setting('edugate.tenant_id')` prüft.
2. Die Tabelle `schultraeger` hat RLS `ENABLE` + `FORCE` und mindestens eine Policy, die `id` gegen `current_setting('edugate.tenant_id')` prüft.

Option 3 wurde verworfen, weil eine redundante, per Konvention identische Spalte ein dauerhaftes Konsistenzrisiko schafft, nur um ein Testmuster zu vereinheitlichen. Option 1 wurde verworfen, weil sie die zweite Verteidigungslinie genau dort aufgibt, wo der spätere `schultraeger-admin` (ADR-005) sie am dringendsten braucht: Mit Option 2 beschränkt der Tenant-Kontext einen Schulträger-Admin automatisch auf die eigene Zeile.

## Konsequenzen

### Positiv

- `V1__initial_schema.sql` ist ohne Interpretationsspielraum umsetzbar.
- Der Wächter-Test ist vollständig und mechanisch ableitbar (zwei Regeln, keine Ausnahme­liste).
- Die spätere Schulträger-Selbstverwaltung erhält ihre Zeilenbeschränkung geschenkt.

### Negativ / Risiken

- Die Wurzeltabelle folgt einem eigenen Policy-Muster (`id` statt `tenant_id`) – im Wächter-Test als eigene Regel abgebildet und damit kontrolliert.

## Nachtrag (Umsetzung des ersten vertikalen Durchstichs): Ausnahme für `audit_admin`

Bei der Implementierung von `V1__initial_schema.sql` (ADR-009) stellte sich heraus, dass
Prüfregel 1 wörtlich genommen auch `audit_admin` erfassen würde: Die Tabelle trägt gemäß
ADR-009 eine (nullable) Spalte `tenant_id`. `audit_admin` ist jedoch kein mandantenbezogener
Fachtabellen-Datensatz im Sinne von ADR-002, sondern das Admin-Audit-Log, das der
Dienstleister-Admin per Definition mandantenübergreifend einsehen kann und das absichtlich
Zeilen mit `tenant_id IS NULL` enthält (z. B. `SCHULTRAEGER_LIST`). Eine Tenant-Policy nach
Regel 1 wäre damit für `audit_admin` semantisch falsch.

**Entscheidung:** `audit_admin` ist von Prüfregel 1 ausgenommen. Die Regel gilt nur für
mandantenbezogene Fachtabellen. Im aktuellen Modell sind das insbesondere `schule` und `schema`;
`svws_instanz` folgt seit ADR-011 nicht mehr dem Tenant-Tabellen-Muster, sondern ist eine
mandantenübergreifende Betriebsressource ohne `tenant_id` mit eigenem RLS-Policy-Muster. Die
Isolation von `audit_admin` erfolgt stattdessen ausschließlich über Tabellenrechte
(`INSERT`/`SELECT` für Anwendungsrollen, kein `UPDATE`/`DELETE` – Append-only, ADR-009). Der
RLS-Wächter-Test bildet die Ausnahme explizit und benannt ab (kein stiller Ausschluss): Er
schließt `audit_admin` namentlich von Prüfregel 1 aus und verifiziert zusätzlich, dass die
Tabelle tatsächlich eine `tenant_id`-Spalte hat (damit die Ausnahme nicht durch eine spätere
Schemaänderung unbemerkt gegenstandslos wird).

## Verweise

- Issue #2, ADR-002 (präzisiert), ADR-009 (Operator-Zugriff)
