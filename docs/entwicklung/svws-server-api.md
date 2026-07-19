# Die API des SVWS-Servers

Zielgruppe: Entwicklerinnen, Entwickler und Coding-Agenten, die an SVWS-EduGate arbeiten und
dafür verstehen müssen, wie der SVWS-Server (das externe System, das EduGate verwaltet und an
das das spätere Gateway durchreicht) seine API strukturiert.

Diese Seite fasst zusammen, was aus den unter [`examples/`](../../examples/) hinterlegten
OpenAPI-3-Beschreibungen des SVWS-Servers hervorgeht, plus Hintergrundwissen, das sich nicht aus
den Spezifikationen selbst erschließt.

**Wichtig:** Die JSON-Dateien unter `examples/` sind manuell hinterlegte Momentaufnahmen, kein
automatisierter Sync mit einer laufenden SVWS-Instanz. Bei sicherheits- oder
korrektheitsrelevanten Fragen (Pfade, Felder, Statuscodes) gegen eine echte Instanz verifizieren,
statt sich allein auf diese Dateien zu verlassen. Versionsstand der Dateien:
`open-api-server.json`/`open-api-external.json` = `1.5.0-SNAPSHOT`,
`open-api-privileged.json` = `1.4.0-SNAPSHOT`.

## Drei getrennte API-Gruppen

Der SVWS-Server stellt drei fachlich unterschiedliche API-Gruppen bereit, jede mit einer eigenen
OpenAPI-Datei. Alle drei verwenden aktuell dasselbe Auth-Schema (`components.securitySchemes.basicAuth`,
`type: http, scheme: basic`) als globalen Default – einzelne Operationen ohne eigene
`security`-Angabe im JSON erben diesen globalen Default, das heißt **nicht** "kein Auth
nötig".

### 1. Server-API (`open-api-server.json`, ~713 Pfade)

Die Haupt-API des SVWS-Servers, gedacht für den SVWS-Web-Client (das Frontend, mit dem Lehrkräfte,
Schulleitung, Schuladministration usw. täglich arbeiten). Fast alle Pfade liegen unter
`/db/{schema}/…` – jede Anfrage ist also auf genau ein Schema (= eine Schule/Umgebung) skaliert.
Größte Bereiche nach Pfadanzahl: `schule`, `gost`, `unterrichtsverteilung`, `schueler`, `lehrer`,
`stundenplan`, `enm`, `benutzer`.

Auth: Basic-Auth mit **schulindividuellen SVWS-Benutzern** (Lehrer, Admin, Schulleitung usw.),
die jeweils eigene, im Schema hinterlegte Rechte haben. Diese Benutzer haben nichts mit den
EduGate-Credentials in `svws_instanz` zu tun.

Für EduGate aktuell nicht direkt relevant (EduGate verwaltet keine fachlichen Schuldaten,
siehe `ARCHITECTURE.md` Kap. 3 „Abgrenzung"), aber das spätere Gateway (Data Plane, ADR-004)
wird typischerweise Anfragen mit Schema-Scope gegen genau diese API weiterleiten.

### 2. Privileged-/Root-API (`open-api-privileged.json`, 45 Pfade)

Die Administrations-API für Schema-Verwaltung: Schemata anlegen, löschen, (de)aktivieren,
migrieren, exportieren/importieren – alles unter `/api/schema/…` bzw. `/api/privileged/…`.

Auth: Basic-Auth mit einem **MariaDB-Datenbankbenutzer**. **Das sind genau die Zugangsdaten, die
EduGate pro `svws_instanz` verschlüsselt ablegt** (ADR-006, `SvwsInstanzCredentialsRequest` /
`SvwsInstanzService.setCredentials`) – mit diesem Benutzer lassen sich über die API Schemata
angelegt, gelöscht und anderweitig verwaltet werden, daher „privilegiert".

Für die echte SVWS-Anbindung der Schul-Schema-Verwaltung nach ADR-014 relevante Endpunkte:

- Liste: `GET /api/schema/liste/svws`, `GET /api/schema/liste/alle`
- Anlegen: `POST /api/schema/create/{schema}` (bzw. `/init/{schulnummer}`, `/{revision}`-Varianten)
- Löschen: `POST /api/schema/root/destroy/{schema}`
- (De-)Aktivieren: `POST /api/schema/root/schema/{schema}/deactivated/{state}`
- Migration: `POST /api/schema/migrate/{schema}/{mariadb|mysql|mssql|mdb}`
- Export/Import: `GET /api/schema/export/{schema}/{sqlite|zip}`, `POST /api/schema/import/{schema}/sqlite`

Diese Endpunkte sind in EduGate aktuell noch nicht produktiv verdrahtet. Die bestehende
Schemaverwaltung schreibt zunächst EduGates eigene PostgreSQL-Tabellen (`schule`, `schema`,
`schema_umgebung`) und setzt neue Schuldatenbanken auf den Status `GEPLANT`. Nur der
Verbindungstest spricht bereits aktiv mit dem SVWS-Server.

**Verbindungstest** (`edugate-core/.../svws/HttpSvwsConnectionTester`): ruft
`POST /api/schema/root/user/checkrootprivs` auf (Request-Body `BenutzerKennwort { user, password }`,
Response-Body ein roher JSON-`Boolean`). Verifiziert gegen eine echte lokale SVWS-Testinstanz
(2026-07-18):

- Der SVWS-Server erzwingt HTTP-Basic-Auth **generisch auf `/api/schema/…`** (Jetty-Ebene, vor
  jeder Methode) – ohne gültigen `Authorization`-Header gibt es 401, unabhängig vom Body.
- Der Endpunkt selbst öffnet zusätzlich, unabhängig vom Basic-Auth-Header, eine direkte
  Datenbankverbindung mit `user`/`password` aus dem JSON-Body und meldet als Ergebnis nur
  `true`/`false` zurück (Quelle: `DBUtilsSchema.checkDBRootUser` im SVWS-Server).
- `HttpSvwsConnectionTester` sendet deshalb dieselben entschlüsselten Zugangsdaten sowohl als
  Basic-Auth-Header als auch im Body – ein Aufruf prüft damit Erreichbarkeit, Passwortgültigkeit
  und privilegierte (root-)Rechte in einem Schritt.
- `checkpwd` (`DBUtilsSchema.checkDBPassword`) ist der schwächere Nachbar-Endpunkt (prüft nur
  allgemeinen Schema-Zugriff, keine root-Rechte) – für `svws_instanz`-Credentials bewusst nicht
  verwendet, da diese explizit privilegiert sein müssen.
- Selbstsignierte Test-SVWS-Instanzen brauchen ein Zertifikat mit passendem Subject Alternative
  Name (SAN) für die tatsächlich verwendete Host/IP, sonst schlägt die Java-Hostname-Verifizierung
  fehl, selbst wenn das Zertifikat selbst vertraut wird (siehe `DevTrustStoreSslContext` für den
  optionalen, rein lokalen Dev-Truststore-Mechanismus).

Sind (noch) keine Zugangsdaten hinterlegt, gibt es nichts, was `checkrootprivs` prüfen könnte.
`SvwsInstanzService.testConnection` weicht in diesem Fall auf
`SvwsConnectionTester#testReachability` aus, das unauthentifiziert `GET /status/alive` aufruft
(bestätigt: dieser Endpunkt verlangt keine Basic-Auth, im Gegensatz zu `/api/schema/…`). Erfolg
setzt den Status dann auf `DEGRADED` statt `OK`, da nur Erreichbarkeit, nicht Zugangsdatengültigkeit
geprüft wurde.

### 3. External-API (`open-api-external.json`, aktuell 4 Pfade)

Der Anfang einer eigenen API-Gruppe, die gezielt für **externe Dienste und Drittanbieter-Software**
freigegeben werden soll – genau dafür wurde SVWS-EduGate geschaffen (Data Plane / Gateway,
ADR-001, ADR-004). Aktuell erst vier Endpunkte unter `/api/external/{schema}/v1/…`
(Lernplattformen-Datenexport: Übersicht, Datenexport je Schuljahresabschnitt, gzip-Variante,
Liste der Schuljahresabschnitte).

Diese Gruppe wird seitens des SVWS-Servers wachsen. **Das spätere Gateway sollte, sobald die
Proxy-Logik ansteht, ausschließlich gegen diese External-API proxyen – nicht gegen Server- oder
Privileged-API**, die beide nicht für den Zugriff durch externe Drittsysteme gedacht sind.

## Historischer Kontext: warum die API manchmal suboptimal wirkt

Der SVWS-Server war ursprünglich nicht darauf ausgelegt, dass große Schulträger ihre Systeme
zentral darüber verwalten (viele Schulen, viele Instanzen, ein Dienstleister). Das war zu Beginn
des SVWS-Projekts noch nicht absehbar. Das erklärt gewachsene, teils uneinheitliche
Pfadstrukturen (z. B. parallel existierende `root`- und Nicht-`root`-Varianten derselben
Funktion, siehe die Migrations-Endpunkte oben).

**Für die Arbeit an EduGate wichtig:** Das ist eine Eigenschaft des externen Systems, keine Sache,
die EduGate „reparieren" sollte. EduGate kapselt die Unzulänglichkeiten der SVWS-API (z. B. über
den `SvwsConnectionTester`-Port, ADR-006-Secret-Handling, das künftige Gateway), statt sie an
Admin-Oberfläche oder Gateway-Clients weiterzureichen.
