# Administration

Zielgruppe: Dienstleister-Admins, die SVWS-EduGate im täglichen Betrieb verwalten.

## Verwaltungsaufgaben

SVWS-EduGate ermöglicht Dienstleister-Admins die Verwaltung der zentralen Betriebs- und
Mandantenstruktur:

- **Schulträger** anlegen, auflisten, anzeigen, bearbeiten, deaktivieren.
- **Schulen** je Schulträger verwalten, inklusive kompakter Schuldatenbank-Zuordnung.
- **SVWS-Instanzen** anlegen, Zugangsdaten hinterlegen, Status prüfen, deaktivieren – als
  geteilte Betriebsressource, die mehreren Schulträgern gleichzeitig dienen kann (siehe
  [ADR-011](../../architecture/adr/ADR-011-svws-instanz-als-geteilte-betriebsressource.md)).
  Kurzbezeichnung und Base-URL sind eindeutig; eine optionale Beschreibung dient als
  Freitext-Betreiberhinweis. Ein Verbindungstest aktualisiert Erreichbarkeitsstatus, Zeitpunkt
  und eine sichere Ergebnis-Meldung – niemals die Zugangsdaten selbst. Sind Zugangsdaten
  hinterlegt, prüft er Erreichbarkeit **und** Gültigkeit/Rechte der Zugangsdaten (Status `OK`
  bei Erfolg); ohne Zugangsdaten prüft er nur die reine Erreichbarkeit (Status `DEGRADED` bei
  Erfolg, da die Zugangsdaten dabei nicht geprüft werden).
- **Schuldatenbanken/Schemata** je Schule und Umgebung einsehen, planen, bearbeiten,
  deaktivieren und einer SVWS-Instanz zuordnen.
- **Schuldatenbank-Übersicht** als eigene fachliche Sicht nutzen, mit Filterung nach
  SVWS-Instanz, Schulträger, Schule, Umgebung, Status und Suchtext.
- **Schema-Umgebungen** wie `PRODUKTIV`, `TEST` oder `SCHULUNG` betreiberseitig verwalten.

## Aktueller Stand

Umgesetzt ist eine brauchbare Admin-Oberfläche für die Control Plane. Sie deckt die oben genannten
Verwaltungsobjekte ab und trennt dabei zwei zentrale Sichten aus ADR-013:

- **SVWS-Instanzen:** Serverperspektive auf betriebene SVWS-Server, Zugangsdaten,
  Verbindungstest und zugeordnete Schuldatenbanken.
- **Schuldatenbanken:** fachliche Schul-/Schema-Perspektive mit Gruppierung und Filterung nach
  Schulträger, Schule, Umgebung und Status.

Das Anlegen oder Bearbeiten einer Schuldatenbank schreibt aktuell nur EduGates eigene
PostgreSQL-Verwaltungsdaten. Es wird noch kein MariaDB-Schema auf dem SVWS-Server angelegt,
gelöscht, migriert oder importiert. Solche echten SVWS-Privileged-API-Operationen sind nach
[ADR-014](../../architecture/adr/ADR-014-verwendung-echter-privileged-api-aufrufe.md) als
geschützte Workflows mit Bestätigung, Berechtigungsprüfung, Statusführung und Audit geplant.

## Rollenmodell (grob)

| Rolle | Bedeutung |
|---|---|
| Dienstleister-Admin | Vollzugriff auf alle Mandanten. Mandantenübergreifende Aktionen (z. B. Schulträger-Verwaltung) laufen über einen gekapselten, auditierten Zugriffspfad – jede solche Aktion erzeugt einen nachvollziehbaren Audit-Eintrag. |
| Schulträger-Admin | Sicht/Verwaltung nur des eigenen Schulträgers – eine spätere Ausbaustufe, aktuell nicht implementiert. |

ADR-015 erweitert dieses grobe Rollenbild zu einem Betreiber- und Gateway-Rechtemodell. Bis dieses
fachlich und technisch umgesetzt ist, sind die produktiv relevanten Admin-Funktionen bewusst auf
`dienstleister-admin` konzentriert.

Der technische Unterbau (Keycloak-Realm, Rollen- und Scope-Details, `OperatorAccess`,
Audit-Modell `audit_admin`) ist in
[ADR-005](../../architecture/adr/ADR-005-keycloak-oidc-rollenmodell.md) und
[ADR-009](../../architecture/adr/ADR-009-operator-zugriff-und-audit.md) beschrieben. Für die
tägliche Administration reicht die Kurzfassung: Verwaltung unterhalb der Mandanten-Wurzel
geschieht im Kontext genau eines Schulträgers; alles, was mandantenübergreifend ist
(Schulträger-Wurzel, SVWS-Instanzen), läuft über den auditierten Operator-Pfad.

## Weiterführend

- [`betreiber/README.md`](../betreiber/README.md) – Betriebsperspektive
- [`sicherheit-compliance/README.md`](../sicherheit-compliance/README.md) – Einordnung von Audit
  und Mandantentrennung
- [ADR-012](../../architecture/adr/ADR-012-schemaverwaltung-und-schuldatenbanken.md) –
  Schemaverwaltung und Schuldatenbanken
- [ADR-013](../../architecture/adr/ADR-013-betreiber-ui-schemaverwaltung.md) – Betreiber-UI für
  SVWS-Instanzen und Schuldatenbanken
