# Administration

Zielgruppe: Dienstleister-Admins, die SVWS-EduGate im täglichen Betrieb verwalten.

## Verwaltungsaufgaben (perspektivisch)

SVWS-EduGate soll Dienstleister-Admins die komfortable Verwaltung der gesamten
Mandantenhierarchie ermöglichen:

- **Schulträger** anlegen, auflisten, anzeigen, bearbeiten, deaktivieren.
- **Schulen** je Schulträger verwalten.
- **SVWS-Instanzen** anlegen, Zugangsdaten hinterlegen, Status prüfen, deaktivieren – als
  geteilte Betriebsressource, die mehreren Schulträgern gleichzeitig dienen kann (siehe
  [ADR-011](../../architecture/adr/ADR-011-svws-instanz-als-geteilte-betriebsressource.md)).
- **Schemas** je Schule und Umgebung (Produktiv, Test) einsehen und den passenden
  SVWS-Instanzen zuordnen.

## Aktueller Stand: Phase 1

Umgesetzt ist bislang der erste vertikale Durchstich: Schulträger anlegen, auflisten, anzeigen,
bearbeiten und deaktivieren – von der Admin-SPA über die Control-Plane-API bis PostgreSQL, mit
aktiver Row-Level Security und auditiertem Admin-Zugriff. Verwaltung von Schulen, SVWS-Instanzen
und Schemas folgt in weiteren Aufträgen. Der jeweils aktuelle Stand ist im
[Repository-Root-`README.md`](../../README.md) dokumentiert.

## Rollenmodell (grob)

| Rolle | Bedeutung |
|---|---|
| Dienstleister-Admin | Vollzugriff auf alle Mandanten. Mandantenübergreifende Aktionen (z. B. Schulträger-Verwaltung) laufen über einen gekapselten, auditierten Zugriffspfad – jede solche Aktion erzeugt einen nachvollziehbaren Audit-Eintrag. |
| Schulträger-Admin | Sicht/Verwaltung nur des eigenen Schulträgers – eine spätere Ausbaustufe, aktuell nicht implementiert. |

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
