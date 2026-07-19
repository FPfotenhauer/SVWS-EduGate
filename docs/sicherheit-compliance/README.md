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
- **Gefährliche SVWS-Operationen:** Echte Schema-Anlage, Migration, Import, Export, Deaktivierung
  und Löschung über die SVWS-Privileged-API sind noch nicht beiläufig an die CRUD-Oberfläche
  gekoppelt. Sie werden nach ADR-014 als eigene Workflows mit Bestätigung, Berechtigungsprüfung,
  Statusführung und Audit geplant.
- **Backup und Restore:** Schulschemata brauchen ein eigenes Backup-/Restore-Konzept, getrennt von
  EduGates PostgreSQL-Metadaten. MariaDB-Dump bzw. MariaDB-Backup sind der vorgesehene Default;
  SVWS-Privileged-API-Export bleibt eine portable Sonderoption. Details:
  [ADR-017](../../architecture/adr/ADR-017-backup-konzept-schulschemata.md).
- **Secret-Export:** Schulbezogene Verbindungsdaten sollen perspektivisch als SOPS/age-
  verschlüsseltes Bundle in Betreiber-Vaults übergeben werden. Privilegierte SVWS-/MariaDB-/Root-
  Secrets gehören nicht in dieses Bundle. Details:
  [ADR-018](../../architecture/adr/ADR-018-exportformat-verbindungsdaten-schuldatenbanken.md).
- **Netzzonen:** Vier Netzzonen trennen Verwaltung, Gateway, externe Zugriffe (spätere
  Ausbaustufe) und SVWS-Instanzen voneinander. Details:
  [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md).
- **Aktuelle Security-Härtung:** Aus dem ersten Security-Review sind offene Härtungsthemen
  dokumentiert: OIDC-Issuer muss produktiv fail-closed sein, OpenAPI/Swagger darf produktiv nicht
  ungeschützt offen liegen, SVWS-Verbindungstests brauchen SSRF-/Egress-Schutz, Defaults wie
  `changeit` dürfen nicht produktiv greifen und Dev-Truststores müssen auf Dev/Test begrenzt sein.

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
- [ADR-014](../../architecture/adr/ADR-014-verwendung-echter-privileged-api-aufrufe.md) –
  Schutzmodell für echte SVWS-Privileged-API-Operationen
- [ADR-016](../../architecture/adr/ADR-016-deployment-und-auslieferungsmodell.md) –
  produktives Auslieferungsmodell
- [ADR-017](../../architecture/adr/ADR-017-backup-konzept-schulschemata.md) – Backup-Konzept
- [ADR-018](../../architecture/adr/ADR-018-exportformat-verbindungsdaten-schuldatenbanken.md) –
  verschlüsselter Export schulbezogener Verbindungsdaten
