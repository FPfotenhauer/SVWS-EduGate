# ADR-017: Backup-Konzept für Schulschemata

- **Status:** proposed
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

ADR-012 beschreibt Schuldatenbanken/Schemata als fachliche Datenbestände einer Schule, die
technisch als MariaDB-Schema auf einer SVWS-Instanz liegen. ADR-014 bereitet echte Aufrufe der
SVWS-Privileged-API vor, darunter Export und Import. Für den produktiven Betrieb fehlt noch ein
Backup-Konzept für diese Schulschemata.

Das Backup-Konzept ist von zwei anderen Sicherungsbereichen zu unterscheiden:

- EduGates eigene PostgreSQL-Datenbank (Control-Plane-Metadaten, Zuordnungen, Audit,
  verschlüsselte Credential-Metadaten) wird im Deployment-/Betriebskonzept gesichert.
- Die eigentlichen Schuldaten liegen im SVWS-Server bzw. dessen MariaDB-Schemata und brauchen ein
  eigenes, Betreiber-taugliches Backup-/Restore-Konzept.

Der SVWS-Server bietet über die Privileged API Export-/Import-Funktionen an, z. B.
`GET /api/schema/export/{schema}/{sqlite|zip}` und `POST /api/schema/import/{schema}/sqlite`.
Ein SQLite-Export ist sehr portabel und kann auch als Migrationsformat dienen. Er ist aber
voraussichtlich langsam, weil die Daten eher in ein SQLite-Format migriert/exportiert werden, statt
als natives MariaDB-Backup abgegriffen zu werden.

Für große Betreiberumgebungen ist außerdem unklar, welche Backup-Infrastruktur bereits vorhanden
ist: zentrale Backup-Software, Storage-Snapshots, MariaDB-Backup-Prozesse oder RZ-Vorgaben. EduGate
soll diese Betreiberrealität nicht verdrängen und kein eigenes, schwergewichtiges Backup-System
erfinden.

## Betrachtete Optionen

1. **Backup ausschließlich über SVWS-Privileged-API als SQLite/ZIP-Export:** sehr portabel und
   unabhängig von MariaDB-Interna. Geeignet für Migration, Einzeltransport und
   plattformübergreifende Wiederherstellung. Für große Bestände aber vermutlich zu langsam und
   nicht die beste Default-Strategie.
2. **Logische MariaDB-Dumps pro Schema:** mit MariaDB-/MySQL-Werkzeugen erzeugbar,
   schema-granular, gut automatisierbar und deutlich näher an der realen Datenhaltung. Restore ist
   nachvollziehbar, aber bei sehr großen Datenbeständen langsamer als physische Backups.
3. **MariaDB-Backup / physisches Backup der MariaDB-Instanz:** performant und für große
   Betreiberbestände die naheliegende Standardmethode. Je nach Tooling eher instanz- oder
   datenbanknah als einzelnes Schul-Schema-portables Artefakt; Restore-Granularität und
   Betriebsprozesse müssen sauber dokumentiert werden.
4. **Beliebige bestehende Betreiber-Backupsoftware integrieren:** langfristig wünschenswert, aber
   zu offen für einen ersten ADR. Ohne Kenntnis der Betreiberumgebung würde EduGate hier zu früh
   fremde Betriebsprozesse modellieren.

## Entscheidung

Vorläufig gewählt wird eine **MariaDB-native Backup-Strategie als Default**, bestehend aus
MariaDB-Dump und MariaDB-Backup. Die SVWS-Privileged-API-Exports bleiben als portable
Sonder-/Migrationsoption vorgesehen, aber nicht als primäre Backup-Methode für große
Betreiberumgebungen.

### Leitbild

- **Default für regelmäßige Sicherung:** MariaDB-native Verfahren.
- **Schema-granulare Sicherung:** MariaDB-Dump pro Schul-Schema, wenn einzelne Schuldatenbanken
  unabhängig gesichert oder transportiert werden sollen.
- **Performance-orientierte Sicherung:** MariaDB-Backup bzw. physische Backup-Verfahren für große
  Instanzen oder viele Schemata.
- **Portable Sonderoption:** SVWS-Privileged-API-Export nach SQLite/ZIP für Migration,
  Einzelfall-Transport, Supportfälle oder Betreiber, bei denen Portabilität wichtiger ist als
  Laufzeit.

EduGate soll sich zunächst auf diese beiden MariaDB-nativen Pfade konzentrieren und keine
allgemeine Integrationsschicht für beliebige Betreiber-Backupsoftware entwerfen.

### Rolle von EduGate

EduGate muss nicht zwingend selbst das Backup-Tool ausführen. Je nach Betreiberumgebung kann
EduGate zunächst:

- Backup-Konzept und empfohlene Verfahren dokumentieren,
- pro SVWS-Instanz anzeigen, ob ein Backup-Konzept hinterlegt ist,
- Metadaten zu letzten Sicherungen, Restore-Tests und Verantwortlichkeiten erfassen,
- Betreiberaktionen wie "Backup anstoßen" oder "Restore testen" später über klar definierte
  Adapter vorbereiten,
- Audit und Nachweisführung unterstützen, ohne Backup-Artefakte selbst speichern zu müssen.

Falls EduGate später Backups selbst auslöst, muss das über eigene geschützte Workflows erfolgen,
nicht als Nebenwirkung von Schema-Bearbeitung.

### Restore ist Teil des Backup-Konzepts

Ein Backup-Konzept ohne Restore-Test ist unvollständig. Für jede gewählte Methode muss geklärt
werden:

- Wie wird ein einzelnes Schul-Schema wiederhergestellt?
- Wie wird eine gesamte SVWS-Instanz wiederhergestellt?
- Wie wird in eine Test-/Migrationsumgebung restored, ohne Produktivdaten zu überschreiben?
- Welche Schritte sind auditiert?
- Welche Credentials werden für Backup und Restore benötigt?
- Wie wird verhindert, dass Restore-Aktionen die Mandantenzuordnung in EduGate verfälschen?

Insbesondere Restore in produktionsnahe oder produktive Schemata ist eine gefährliche Operation im
Sinne von ADR-014 und braucht Bestätigung, Berechtigungsprüfung, Audit und ggf. ein späteres
Vier-Augen- oder Freigabemodell.

### Sicherheits- und Betriebsanforderungen

Für Schulschema-Backups gelten mindestens:

- Verschlüsselung bei Speicherung und Transport,
- Zugriff nur für berechtigte Betreiberrollen,
- keine Backup-Secrets in Logs, UI oder Auditdetails,
- Aufbewahrungs- und Löschfristen je Betreiberkonzept,
- getrennte Betrachtung von Produktiv-, Test-, Schulungs-, Migrations- und Archivschemata,
- regelmäßige Restore-Proben,
- Dokumentation von RPO/RTO-Zielen je Betreiber oder Betriebsstufe.

Die konkreten RPO/RTO-Werte werden noch nicht festgelegt, müssen aber später Teil der
Betriebsdokumentation werden.

## Konsequenzen

### Positiv

- Die Default-Strategie passt zur tatsächlichen Datenhaltung in MariaDB und ist performanter als
  ein generischer SQLite-Export über die Privileged API.
- Der portable SQLite/ZIP-Weg bleibt verfügbar, wird aber nicht fälschlich als primäre
  Massensicherungsstrategie verkauft.
- EduGate bleibt kompatibel mit bestehenden Betreiber-Backupkonzepten, ohne sie schon jetzt im
  Detail kennen zu müssen.
- Restore-Tests und Audit werden früh als notwendiger Teil des Backup-Konzepts festgehalten.

### Negativ / Risiken

- MariaDB-native Backups sind weniger portabel als SQLite-Exporte.
- Physische Backups können stärker an Betreiberinfrastruktur, Storage und MariaDB-Versionen
  gekoppelt sein.
- EduGate braucht später klare UI- und Adapterentscheidungen, falls Backups direkt aus der
  Anwendung angestoßen oder überwacht werden sollen.
- Ohne konkrete Betreiberanforderungen bleiben RPO/RTO, Aufbewahrung und technische Integration
  zunächst offen.

## Verweise

- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-014 (Verwendung echter Aufrufe der SVWS-Privileged-API)
- ADR-015 (Rollen- und Rechte-Management)
- ADR-016 (Deployment- und Auslieferungsmodell)
- `docs/entwicklung/svws-server-api.md`
- `examples/open-api-privileged.json`
