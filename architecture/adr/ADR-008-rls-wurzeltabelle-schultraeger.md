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

## Verweise

- Issue #2, ADR-002 (präzisiert), ADR-009 (Operator-Zugriff)
