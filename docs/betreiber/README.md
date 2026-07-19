# Betreiber

Zielgruppe: Rechenzentrum, Dienstleisterbetrieb, technischer Betrieb von SVWS-EduGate.

## Betriebsrolle von SVWS-EduGate

SVWS-EduGate wird im BSI-zertifizierten Rechenzentrum des Dienstleisters betrieben, der
SVWS-Server für mehrere Schulträger führt. Es besteht aus zwei getrennt betriebenen Diensten
(Control Plane und Data Plane / Gateway) und greift auf Schuldaten ausschließlich über die
SVWS-Server-API zu – nie direkt auf die MariaDB der SVWS-Server (siehe
[`architecture/ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md), Kapitel 1 und 2).

## Grobe Komponentenübersicht

| Komponente | Rolle |
|---|---|
| Admin-SPA (Frontend) | Oberfläche für Dienstleister-Admins, spricht mit der Control Plane. |
| Control Plane | Verwaltung von Schulträgern, Schulen, SVWS-Instanzen, Schemas. |
| Gateway (Data Plane) | Mandantengetrennter API-Zugriff für interne (später externe) API-Clients. |
| Keycloak | Zentrale Authentifizierung/Autorisierung (OIDC) für Admins und API-Clients. |
| PostgreSQL | Verwaltungs- und Mandantendaten von EduGate, mit Row-Level Security. |
| SVWS-Instanzen | Vom Dienstleister betriebene SVWS-Server samt MariaDB-Schemata der Schulen – geteilte Betriebsressource, keine feste 1:1-Zuordnung zu einem Schulträger. |

Details zu Containern, Netzzonen und Laufzeitverhalten: Kapitel 5–7 der
[`ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md).

## Netzzonen, Keycloak, PostgreSQL, Container-Betrieb

- **Netzzonen:** Vier Zonen (Verwaltungsnetz, Gateway-Zone, externe Zone als spätere
  Ausbaustufe, SVWS-Zone) trennen Verwaltung, Gateway und SVWS-Instanzen von Beginn an –
  Details in [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md).
- **Keycloak:** eigener Realm `edugate`, liegt in der Verwaltungszone, wird nie extern exponiert
  – Details in [ADR-005](../../architecture/adr/ADR-005-keycloak-oidc-rollenmodell.md).
- **PostgreSQL:** hält Verwaltungs- und Mandantendaten mit erzwungener Row-Level Security; der
  Gateway-Dienst besitzt dafür nur einen lesenden, eingeschränkten DB-Zugang – Details in
  [ADR-002](../../architecture/adr/ADR-002-mandantenmodell-postgresql-rls.md) und
  [ADR-008](../../architecture/adr/ADR-008-rls-wurzeltabelle-schultraeger.md).
- **Container-Betrieb:** Auslieferung als Container-Images, lokale Entwicklung über Docker
  Compose. Das produktive Auslieferungsmodell ist in
  [ADR-016](../../architecture/adr/ADR-016-deployment-und-auslieferungsmodell.md) festgehalten:
  Betreiber sollen keine IDE installieren und nicht aus dem Source-Tree bauen müssen.
- **Backup:** EduGates PostgreSQL-Datenbank, Keycloak-Konfiguration und Master-Key brauchen eigene
  Betreiber-Backups. Für Schulschemata ist MariaDB-native Sicherung per Dump oder MariaDB-Backup
  als Default vorgesehen; SQLite/ZIP-Export über die SVWS-Privileged-API bleibt eine portable
  Sonderoption. Details: [ADR-017](../../architecture/adr/ADR-017-backup-konzept-schulschemata.md).
- **Secret-Export:** Schulbezogene Verbindungsdaten sollen perspektivisch als
  SOPS-verschlüsseltes YAML/JSON mit `age` exportierbar sein, damit Betreiber sie in gängige
  Vaults oder GitOps-Prozesse übernehmen können. Details:
  [ADR-018](../../architecture/adr/ADR-018-exportformat-verbindungsdaten-schuldatenbanken.md).

## Abgrenzung

Diese Seite beschreibt die Betriebsrolle und verweist auf die zuständigen ADRs. Sie enthält
**keine** produktiven Secrets, keine konkreten internen Netzdetails (Hostnamen, IP-Bereiche,
Firewall-Regeln) und keine Passwörter. Solche Werte gehören ausschließlich in das
Secret-Management und die Netzwerkkonfiguration des jeweiligen Betreibers, niemals in dieses
Repository (vgl. `.env.example` im Repository-Root, das nur Platzhalter enthält).

## Checkliste: vor produktivem Betrieb zu klären

- [ ] Zielplattform des produktiven Betriebs festlegen (Docker Compose, Kubernetes oder
      RZ-spezifische Plattform).
- [ ] Produktive Deployment-Artefakte und Update-Prozess nach
      [ADR-016](../../architecture/adr/ADR-016-deployment-und-auslieferungsmodell.md) festlegen;
      die lokale `docker-compose.yml` ist keine unveränderte Produktionsvorlage.
- [ ] Firewall-Regeln zwischen den vier Netzzonen entsprechend
      [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md) mit dem RZ-Betrieb/ISB
      abstimmen.
- [ ] Backup- und Restore-Verfahren für PostgreSQL festlegen und testen.
- [ ] Backup- und Restore-Verfahren für Keycloak-Konfiguration und Realm-Daten festlegen.
- [ ] Backup-/Recovery-Verfahren für den Master-Key der Credential-Verschlüsselung
      (`EDUGATE_MASTER_KEY`, siehe [ADR-006](../../architecture/adr/ADR-006-secret-handling-svws-credentials.md))
      festlegen und testen. Zugangsdaten und Verbindungstest-Ergebnisse dürfen dabei nie im
      Klartext in Logs erscheinen – die Control Plane protokolliert nur eine sichere,
      generische Ergebnis-Meldung ohne Secrets oder interne Details.
- [ ] Backup- und Restore-Konzept für SVWS-Schulschemata nach
      [ADR-017](../../architecture/adr/ADR-017-backup-konzept-schulschemata.md) konkretisieren:
      MariaDB-Dump, MariaDB-Backup, Restore-Proben, RPO/RTO, Aufbewahrung, Verschlüsselung.
- [ ] Vault-/Secret-Management für schulbezogene External-API-Verbindungsdaten festlegen; der
      geplante SOPS/age-Export nach
      [ADR-018](../../architecture/adr/ADR-018-exportformat-verbindungsdaten-schuldatenbanken.md)
      ersetzt kein Betreiber-Vault.
- [ ] mTLS zu den SVWS-Instanzen einrichten, sobald deren Zertifikatskonfiguration das erlaubt.
- [ ] Erreichbarkeit der konfigurierten SVWS-Instanz-Base-URLs aus der Verwaltungszone
      sicherstellen (Firewall/Routing gemäß [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md)),
      da der Verbindungstest der Control Plane sonst grundsätzlich fehlschlägt.
- [ ] Aufbewahrungsfristen für Admin- und Gateway-Audit-Daten festlegen.
- [ ] Security-Härtung vor Produktivbetrieb abarbeiten, insbesondere OIDC-Issuer fail-closed,
      Swagger/OpenAPI nicht öffentlich produktiv exponieren, SSRF-Schutz für SVWS-Verbindungstests,
      keine Default-Passwörter und kein Dev-Truststore in Produktion.
- [ ] Konkrete BSI-Grundschutz-Bausteinliste mit dem RZ-Betrieb/ISB abgleichen (siehe
      [`compliance/bsi-grundschutz-mapping.md`](../../compliance/bsi-grundschutz-mapping.md)).

Weitere offene Punkte: [`compliance/bsi-grundschutz-mapping.md`](../../compliance/bsi-grundschutz-mapping.md)
(„Offene Klärungen“) und [`compliance/nachweis-checkliste.md`](../../compliance/nachweis-checkliste.md)
(„Spätere Betriebsnachweise“).
