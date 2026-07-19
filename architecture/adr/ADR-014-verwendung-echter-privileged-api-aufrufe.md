# ADR-014: Verwendung echter Aufrufe der SVWS-Privileged-API

- **Status:** proposed
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

ADR-012 beschreibt die Schemaverwaltung fachlich und nennt die relevanten Endpunkte der
SVWS-Privileged-/Root-API: Schemata listen, anlegen, deaktivieren, löschen, migrieren,
exportieren und importieren. Der aktuelle POC der Schemaverwaltung führt diese Operationen noch
nicht gegen eine echte SVWS-Instanz aus. `SchuleService` und `SchemaService` lesen und schreiben
zunächst ausschließlich EduGate-interne PostgreSQL-Tabellen (`schule`, `schema`). Ein neu
angelegtes Schema mit Status `GEPLANT` existiert damit nur in EduGate, nicht als MariaDB-Schema
auf dem SVWS-Server.

Der einzige aktuelle Code-Pfad, der tatsächlich mit dem SVWS-Server spricht, ist der
Verbindungstest: ohne Zugangsdaten `GET /status/alive`, mit Zugangsdaten
`POST /api/schema/root/user/checkrootprivs`. Das prüft Erreichbarkeit und privilegierte
Credentials, verändert aber keine SVWS-Daten.

Sobald EduGate echte Privileged-API-Aufrufe ausführt, werden aus vorbereiteten
Verwaltungsdatensätzen reale Betriebsoperationen. Das betrifft insbesondere:

- `GET /api/schema/liste/svws`, `GET /api/schema/liste/alle` für Sync/Abgleich,
- `POST /api/schema/create/{schema}` für tatsächliches Anlegen eines Schemas,
- `POST /api/schema/root/schema/{schema}/deactivated/{state}` für Deaktivieren/Reaktivieren,
- `POST /api/schema/root/destroy/{schema}` für Löschen,
- `POST /api/schema/migrate/...` für Migration,
- `GET /api/schema/export/...` und `POST /api/schema/import/...` für Export/Import.

Diese Operationen sind sicherheits- und betriebsrelevant. Fehler können produktive
Schuldatenbanken betreffen, Mandantenzuordnung verfälschen, Migrationen abbrechen oder Daten
unbeabsichtigt löschen. Sie dürfen deshalb nicht als direkte Nebenwirkung einfacher CRUD-Buttons
entstehen.

## Betrachtete Optionen

1. **Privileged-API direkt in bestehenden CRUD-Services aufrufen:** schnell umzusetzen, aber
   gefährlich. Ein Klick auf "Schuldatenbank anlegen" hätte sofort Außenwirkung auf dem
   SVWS-Server, ohne eigenständigen Schutz-, Audit- und Fehlerworkflow.
2. **Echte SVWS-Operationen als eigene, geschützte Betreiber-Workflows modellieren:** EduGate
   behält das interne Planungsmodell (`GEPLANT`, Metadaten, Zuordnung) und führt echte
   Privileged-API-Operationen nur über explizite Kommandos mit Bestätigung, Audit, Statusübergang,
   Fehlerprotokoll und ggf. asynchroner Ausführung aus.
3. **Privileged-API dauerhaft außerhalb von EduGate lassen:** Betreiber nutzen weiter den
   SVWS-Admin-Client oder manuelle Prozesse. Das vermeidet Risiko in EduGate, widerspricht aber dem
   Ziel, große Betreiberumgebungen zentral und komfortabel zu verwalten.

## Entscheidung

Vorläufig gewählt wird **Option 2**.

EduGate integriert die SVWS-Privileged-API schrittweise und gekapselt. Die Integration wird nicht
als direkte Erweiterung der CRUD-Services gebaut, sondern als eigener Anwendungsbereich
geschützter SVWS-Operationen.

### Grundregeln

- Alle echten SVWS-Aufrufe laufen über dedizierte Ports/Adapter, z. B. `SvwsPrivilegedApiClient`.
- Controller und UI lösen fachliche Kommandos aus, keine rohen SVWS-API-Pfade.
- Die historisch gewachsene SVWS-API wird hinter EduGate-Services gekapselt; Pfadnamen und
  uneinheitliche SVWS-Details dürfen nicht in die Admin-UI durchsickern.
- Jede schreibende oder gefährliche Operation ist auditpflichtig.
- Keine Operation zeigt Secrets in UI, Logs, API-Antworten oder Auditdetails.
- Operationen müssen idempotent oder zumindest wiederholbar/prüfbar entworfen werden, soweit die
  SVWS-API dies zulässt.
- Timeout, Retry, Fehlerklasse und Abbruchzustand werden pro Operationsart bewusst behandelt.

### Stufenmodell

1. **Read-only Sync zuerst:** EduGate liest vorhandene Schemata je SVWS-Instanz und zeigt sie als
   bekannte oder unzugeordnete Funde an. Keine automatische Tenant-Zuordnung allein aus dem
   Schemanamen.
2. **Anlegen geplanter Schemata:** Ein `GEPLANT`-Schema kann nach expliziter Bestätigung auf dem
   SVWS-Server angelegt werden. Erst danach wechselt der Status z. B. zu `VORHANDEN` oder `AKTIV`.
3. **Deaktivieren/Reaktivieren:** Statusänderungen auf dem SVWS-Server werden getrennt vom
   EduGate-internen Aktiv-Flag behandelt und auditiert.
4. **Migration und Import/Export:** Diese Operationen erhalten eigene Workflows mit Vorprüfung,
   Fehlerliste, Testlauf/Vorschau soweit möglich, expliziter Bestätigung und klarer Anzeige, ob
   Produktiv-, Test- oder Migrationsschemata betroffen sind.
5. **Löschen/Destroy zuletzt:** Physisches Löschen ist die riskanteste Operation und wird erst
   nach ausgereiftem Schutzkonzept angebunden. Ein Vormerken zur Löschung kann früher sinnvoll
   sein als unmittelbares `destroy`.

### Schutzmechanismen

Für gefährliche Operationen gelten mindestens:

- klare Kontextanzeige: Instanz, Schule, Schulträger, Schema, Umgebung, Status,
- explizite Bestätigung vor Ausführung,
- rollenbasierte Berechtigungsprüfung (siehe ADR-015),
- Audit-Eintrag vor/nach Ausführung mit Ergebnisstatus,
- strukturierte Fehlerdetails ohne Secrets,
- sichtbarer Operationsstatus für lang laufende Vorgänge,
- keine implizite Ausführung beim Speichern eines EduGate-Stammdatensatzes.

Für besonders riskante Operationen wie Produktivmigration, Import/Restore oder Destroy wird in
einer späteren Verfeinerung geprüft, ob Vier-Augen-Prinzip, Freigabequeue oder zeitverzögerte
Ausführung erforderlich sind.

## Konsequenzen

### Positiv

- Der aktuelle POC bleibt als sicheres Planungsmodell nutzbar.
- Echte SVWS-Operationen werden kontrolliert, auditierbar und testbar eingeführt.
- Read-only Sync kann früh Mehrwert liefern, ohne sofort produktive Daten zu verändern.
- ADR-013 kann die UI-Schutzmechanismen sauber vorgeben, bevor die gefährlichen Operationen
  technisch angebunden werden.

### Negativ / Risiken

- Mehr Implementierungsaufwand als direkte API-Aufrufe aus CRUD-Services.
- Für Operationen mit Außenwirkung braucht es zusätzliche Zustandsmodelle, Tests und
  Fehlerbehandlung.
- Die SVWS-API ist historisch gewachsen; EduGate muss Besonderheiten kapseln, ohne sie
  schönzureden oder unsichtbar gefährlich zu machen.

## Verweise

- ADR-006 (Secret-Handling für SVWS-Zugangsdaten)
- ADR-009 (Operator-Zugriff und Audit)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-013 (Betreiber-UI für Schemaverwaltung und Schuldatenbanken)
- `docs/entwicklung/svws-server-api.md`
- `examples/open-api-privileged.json`
