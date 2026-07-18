# Sicherheit &amp; Compliance

Zielgruppe: ISB, Datenschutz, Audit, Projektverantwortliche.

Diese Seite ist ein Einstiegspunkt und ordnet die vorhandenen Dokumente kurz ein. Sie ersetzt
weder die Architekturdokumentation noch die Compliance-Dokumente selbst.

## Wo liegt was?

| Ordner | Inhalt |
|---|---|
| [`architecture/ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md) | Gesamtarchitektur inkl. Qualitätszielen, Randbedingungen (BSI IT-Grundschutz, DSGVO) und Risiken. |
| [`architecture/adr/`](../../architecture/adr/README.md) | Einzelne, verbindliche Architekturentscheidungen (ADRs). |
| [`compliance/README.md`](../../compliance/README.md) | Projektbezogene Sicherheits- und Compliance-Dokumentation: BSI-Grundschutz-Mapping, Schutzbedarfsfeststellung, Risikoannahmen, Nachweis-Checkliste. |

## Kurze Einordnung zentraler Themen

- **Mandantentrennung:** Jede mandantenbezogene Tabelle trägt eine Mandanten-Kennung; PostgreSQL
  Row-Level Security mit `FORCE` ist die zweite Verteidigungslinie und für alle Rollen wirksam.
  Details: [ADR-002](../../architecture/adr/ADR-002-mandantenmodell-postgresql-rls.md),
  [ADR-008](../../architecture/adr/ADR-008-rls-wurzeltabelle-schultraeger.md).
- **RLS und Operator-Zugriff:** Mandantenübergreifende Admin-Operationen (z. B. Verwaltung der
  Schulträger-Wurzel oder der SVWS-Instanzen) laufen ausschließlich über einen gekapselten,
  auditierten Zugriffspfad (`OperatorAccess`) statt über ein pauschales Umgehen der
  Row-Level Security. Details: [ADR-009](../../architecture/adr/ADR-009-operator-zugriff-und-audit.md).
- **Audit:** Admin-Operationen (`audit_admin`) und Gateway-Zugriffe werden getrennt
  protokolliert und sind manipulationssicher (append-only). Details: Kapitel 8 der
  [`ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md) sowie
  [ADR-009](../../architecture/adr/ADR-009-operator-zugriff-und-audit.md).
- **Secret-Handling:** SVWS-Zugangsdaten werden verschlüsselt (AES-256-GCM) abgelegt, verlassen
  nie Frontend oder Logs. Details: [ADR-006](../../architecture/adr/ADR-006-secret-handling-svws-credentials.md).
- **Netzzonen:** Vier Netzzonen trennen Verwaltung, Gateway, externe Zugriffe (spätere
  Ausbaustufe) und SVWS-Instanzen voneinander. Details:
  [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md).

## Einordnung

Dieser Bereich **ersetzt keine formale Betreiber-Zertifizierung** und keine formale
Schutzbedarfsfeststellung oder Risikoanalyse des Betreibers. Er sammelt projektbezogene
Nachweise, Annahmen und Orientierung, wie sie im laufenden Projekt entstehen – als
nachvollziehbare Brücke zwischen Architekturentscheidungen, Umsetzung und späteren, formalen
Nachweisen des Betreibers. Details zur Arbeitsweise: [`compliance/README.md`](../../compliance/README.md).

## Weiterführend

- [`betreiber/README.md`](../betreiber/README.md) – betriebliche Checkliste vor produktivem
  Einsatz
- [`compliance/nachweis-checkliste.md`](../../compliance/nachweis-checkliste.md) – Status
  einzelner Nachweise
