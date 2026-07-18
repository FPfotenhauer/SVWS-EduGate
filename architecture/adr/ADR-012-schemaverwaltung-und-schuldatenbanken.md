# ADR-012: Schemaverwaltung und Schuldatenbanken

- **Status:** accepted
- **Datum:** 2026-07-18
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Nach der SVWS-Serververwaltung folgt die Verwaltung der **Schuldatenbanken**. Im SVWS-Kontext sind
das technisch MariaDB-Schemas auf einer SVWS-Server-Instanz; fachlich sind es die Datenbestände
einer Schule für eine bestimmte Umgebung, z. B. Produktion, Test oder Schulung.

Die fachlichen Grundregeln sind:

- Ein Schulträger kann mehrere Schulen haben.
- Eine Schule gehört immer zu genau einem Schulträger.
- Betreiber können mehrere Schulträger auf derselben SVWS-Server-Instanz betreiben
  (ADR-011: `svws_instanz` ist eine geteilte, mandantenübergreifende Betriebsressource).
- Eine Schule kann mehrere Schuldatenbanken/Schemata haben, z. B. Produktiv, Test, Schulung,
  Migration oder Archiv.
- Die SVWS-Privileged-/Root-API kann diese Schemata verwalten: listen, anlegen, löschen,
  deaktivieren, migrieren, exportieren und importieren.

Ohne präzises Modell drohen zwei Fehlentwicklungen:

1. `svws_instanz` könnte wieder implizit als einem Schulträger gehörend behandelt werden, obwohl
   ADR-011 genau diese Annahme verworfen hat.
2. Schemata könnten als einfache technische Namen verwaltet werden, ohne die fachliche Bindung an
   Schulträger, Schule, Umgebung, Lebenszyklus, Credentials, Audit und spätere Massenverwaltung
   sauber abzubilden.

## Betrachtete Optionen

1. **Schema als rein technische Liste je SVWS-Instanz:** EduGate liest per Privileged API die
   vorhandenen Schemanamen und verwaltet sie direkt unter der Instanz. Das ist einfach, verliert
   aber den fachlichen Kontext: Schulträger, Schule, Umgebung, Status und spätere Gateway-Regeln
   müssten nachträglich rekonstruiert werden.
2. **Schema als Kind der Schule, mit Verweis auf die hostende SVWS-Instanz:** Jedes Schema gehört
   zu genau einer Schule und damit zu genau einem Schulträger, liegt aber technisch auf einer
   mandantenübergreifenden SVWS-Instanz. Die Mandantentrennung läuft über `tenant_id`/Schule/Schema,
   nicht über die Instanz.
3. **Explizite n:m-Zuordnung Schulträger ↔ SVWS-Instanz plus Schemata darunter:** Das bildet
   geteilte Instanzen sichtbar ab, führt aber eine zusätzliche Zuordnungsebene ein, die fachlich
   erst durch konkrete Schemata relevant wird. Für reine Betreiber-Reports kann diese Beziehung
   aus den Schema-Zuordnungen abgeleitet werden.

## Entscheidung

Gewählt wurde **Option 2**.

### Fachliches Modell

Das Schemamodell folgt dieser Kette:

```text
Schulträger
  -> Schule
      -> SVWS-Schema / Schuldatenbank
           -> liegt auf SVWS-Instanz
```

`SVWS-Instanz` bleibt dabei eine geteilte Betriebsressource nach ADR-011:

```text
SVWS-Instanz
  -> hostet Schemata mehrerer Schulen
  -> diese Schulen können zu mehreren Schulträgern gehören
```

Ein Schema ist damit **mandantenbezogen**, die Instanz nicht. Autorisierung und RLS dürfen niemals
allein aus der Instanz abgeleitet werden. Maßgeblich ist immer die vollständige Kette:

```text
Tenant/Schulträger -> Schule -> Schema -> Instanz
```

### Tabellen und Kernfelder

Die bestehende Tabelle `schema` wird fachlich als **SVWS-Schema / Schuldatenbank** verstanden und
bleibt tenant-gebunden.

Mindestens abzubilden sind:

- `id`
- `tenant_id` (Schulträger, RLS-relevant)
- `schule_id`
- `instanz_id`
- `schema_name`
- `umgebung`
- `status`
- `aktiv`
- `beschreibung` oder Betreiberhinweis (optional)
- `source`/`verwaltungsart` für manuell angelegt vs. aus SVWS synchronisiert/importiert
- Zeitstempel für Erstellung, Änderung, letzte Synchronisation und letzten Verbindungstest,
  soweit implementiert

`schema_name` ist technisch pro `svws_instanz` eindeutig. Fachlich gelten zusätzlich:

- Eine Schule darf höchstens ein aktives Produktiv-Schema haben.
- Eine Schule darf mehrere Nicht-Produktiv-Schemata haben, z. B. Test, Schulung, Migration oder
  Archiv.
- Die Umgebung darf nicht dauerhaft auf nur `PRODUKTIV | TEST` verengt werden. `PRODUKTIV` und
  `TEST` sind Startwerte, aber Betreiber brauchen erweiterbare Werte wie `SCHULUNG`, `ABNAHME`,
  `MIGRATION`, `ARCHIV`, `DEMO` oder lokale Varianten.

### Namenskonventionen für Schemata

EduGate muss sinnvolle, betreiberfreundliche Namenskonventionen für SVWS-Schemata anbieten. Die
Privileged API arbeitet mit technischen Schemanamen; diese Namen werden später in Massenverwaltung,
Import/Export, Migration, Support und Fehleranalyse häufig verwendet. Sie dürfen daher nicht rein
zufällig oder nur UI-intern entstehen.

Grundlage der Standardkonvention ist die **Schulnummer**:

- Eine Schule hat in der Regel eine sechsstellige Schulnummer, z. B. `123456`.
- Ob es in Ausnahmefällen Schulen ohne Schulnummer geben kann, ist offen. Das Modell darf solche
  Sonderfälle nicht unmöglich machen, muss sie aber explizit sichtbar behandeln.
- Schemanamen werden nicht allein aus einem freien Anzeigenamen erzeugt.

EduGate soll eine Standard-Namenskonvention vorschlagen, z. B.:

```text
svws_<schulnummer>_<umgebung>
```

Beispiele:

```text
svws_123456_prod
svws_123456_test
svws_123456_schulung
```

Die konkrete Konvention ist Betreiberkonfiguration, aber sie muss folgende Eigenschaften haben:

- eindeutig pro `svws_instanz`
- stabil über Import/Synchronisation hinweg
- maschinenlesbar und sortierbar
- kompatibel mit MariaDB-Schemanamen
- ohne personenbezogene Daten
- ohne interne Betreibergeheimnisse
- erklärbar in Exporten und Betriebsdokumentation

Für Schulen ohne belastbare Schulnummer braucht es einen Sonderpfad:

- explizit vergebener technischer Schlüssel statt stillschweigender Ableitung
- sichtbare Warnung/Markierung in der UI
- Audit-Eintrag bei Vergabe oder Änderung
- keine automatische Vermutung aus Schulname oder Freitext

Die Implementierung darf mit einer einfachen Standardkonvention starten, muss aber so geschnitten
sein, dass Betreiber die Konvention später konfigurieren oder durch Regeln erweitern können.

### Umgebung und Status

`umgebung` beschreibt den fachlichen Zweck des Datenbestands. Sie ist nicht identisch mit dem
technischen Lebenszyklus.

Der technische/fachliche Status eines Schemas wird separat geführt, z. B.:

- `GEPLANT` - nur in EduGate angelegt, noch nicht auf dem SVWS-Server vorhanden
- `VORHANDEN` - auf der SVWS-Instanz gefunden
- `AKTIV` - aktiv nutzbar
- `DEAKTIVIERT` - auf SVWS-Seite deaktiviert
- `MIGRATION_ERFORDERLICH` - Revision/Version passt nicht zum Zielstand
- `FEHLER` - letzter Abgleich oder letzte Operation fehlgeschlagen
- `ARCHIVIERT` - fachlich nicht mehr aktiv, aber historisch nachweisbar

Die genaue Enum-Ausprägung darf in der Implementierung kleiner starten, muss aber diese Trennung
von Umgebung und Status erhalten.

### Abgleich mit der SVWS-Privileged-API

EduGate darf nicht nur manuelle Datensätze verwalten. Die Schemaverwaltung muss auf späteren
Abgleich mit der SVWS-Privileged-/Root-API vorbereitet sein.

Relevante API-Funktionen sind u. a.:

- Schemata listen: `GET /api/schema/liste/svws`, `GET /api/schema/liste/alle`
- Schema anlegen: `POST /api/schema/create/{schema}` und Varianten mit Initialisierung/Revision
- Schema deaktivieren: `POST /api/schema/root/schema/{schema}/deactivated/{state}`
- Schema löschen: `POST /api/schema/root/destroy/{schema}`
- Migration: `POST /api/schema/migrate/{schema}/{mariadb|mysql|mssql|mdb}` und Varianten
- Export/Import: `GET /api/schema/export/{schema}/{sqlite|zip}`,
  `POST /api/schema/import/{schema}/sqlite`

Diese SVWS-API-Strukturen sind historisch gewachsen und teilweise uneinheitlich. EduGate kapselt
sie hinter eigenen Services/Ports und reicht sie nicht unverändert an Admin-Oberfläche oder
Gateway-Clients durch.

### Unzugeordnete und importierte Schemata

Beim Abgleich einer SVWS-Instanz können Schemata gefunden werden, die EduGate noch keiner Schule
zuordnen kann.

Die Implementierung muss dafür einen Konzeptpfad vorsehen:

- unbekanntes Schema wird als Import-/Synchronisationsfund sichtbar
- Betreiber kann es einer Schule zuordnen
- damit ist automatisch der Schulträger/Tenant bestimmt
- nicht zuordenbare Funde bleiben nachvollziehbar, dürfen aber nicht stillschweigend einem
  falschen Tenant zugeordnet werden

Ob unzugeordnete Funde in einer eigenen Tabelle oder als Status/Import-Queue geführt werden, ist
eine Implementierungsentscheidung. Entscheidend ist: Keine automatische Mandantenzuordnung allein
aus Schemanamen raten, ohne Review-Möglichkeit.

### Credentials

Credentials werden getrennt betrachtet:

- Privilegierte MariaDB-/Root-Credentials der SVWS-Instanz dienen der Schema-Verwaltung über die
  Privileged API. Sie gehören zur `svws_instanz` und werden nach ADR-006 verschlüsselt abgelegt.
- Spätere schema- oder schulbezogene Credentials für Gateway-Zugriffe sind ein eigener
  Credential-Typ und dürfen nicht mit den privilegierten Instanz-Credentials vermischt werden.

Beim Anlegen, Migrieren oder Einspielen eines Backups über die Privileged API müssen in der Regel
MariaDB-Benutzername und MariaDB-Passwort für das betroffene Schema erzeugt oder gesetzt werden.
Auch dafür braucht EduGate Konventionen:

- Benutzername aus stabilen technischen Bestandteilen ableiten, z. B. Schulnummer, Umgebung und
  ggf. kurzer Betreiber-/Instanzpräfix.
- Benutzername eindeutig pro SVWS-Server/MariaDB-Kontext halten.
- Passwort immer kryptografisch zufällig erzeugen; keine Ableitung aus Schulnummer,
  Schemaname oder Umgebung.
- Credential-Metadaten getrennt vom geheimen Wert speichern: Zweck, Umgebung, Schema, erzeugt am,
  erzeugt von, letzter Export, letzter Test, ggf. Rotationserfordernis.
- Credentials dürfen ersetzbar und später rotierbar sein, ohne das fachliche Schema-Objekt neu
  anzulegen.

Auch hier gilt: EduGate bietet eine sichere Standardkonvention, erzwingt aber keine zu enge
Betreiberrealität. Viele Dienstleister werden bestehende Namensmuster oder Safe-Strukturen haben.

Credentials erscheinen niemals im Frontend, in API-Antworten, Logs oder Audit-Details im Klartext.
Massenverwaltung von Credentials muss später über Import, Vorschau, Validierung und sichere
Fehlerberichte erfolgen, nicht über manuelles Einzelabtippen.

### Credential-Verwaltung und Export

EduGate ist nicht der einzige Ort, an dem Betreiber Zugangsdaten verwalten müssen. Dienstleister
brauchen oft einen externen Safe, ein Passwortmanagement-System oder ein separates
Betriebshandbuch. Deshalb muss EduGate einen sicheren Exportpfad vorsehen.

Grundregeln:

- Klartext-Credential-Export nur als explizite, auditierte Aktion.
- Kein automatischer Export bei jeder Änderung.
- Export nur für berechtigte Dienstleister-Admins.
- Exportdateien müssen ein klar dokumentiertes Format haben, z. B. CSV oder JSON.
- Export muss für externe Safes nutzbar sein: Schema, Schule, Schulnummer, Umgebung,
  SVWS-Instanz, Benutzername, Passwort, Erzeugungszeitpunkt, Zweck.
- Export darf keine unnötigen personenbezogenen Daten enthalten.
- Nach Möglichkeit Export verschlüsselt oder mindestens mit klarer Warnung und kurzem
  Download-Zeitfenster.
- Jeder Export wird auditiert, aber ohne Passwortwerte im Audit.

Spätere Integrationen mit einem echten Passwort-Safe oder Secret-Manager bleiben möglich. Der
erste Schritt darf ein manuell herunterladbarer Export sein, solange er bewusst, geschützt und
auditierbar ist.

### Massenverwaltung

Die Schemaverwaltung muss von Beginn an für große Betreiberumgebungen vorbereitet werden:

- mehrere Schulträger pro Instanz
- viele Schulen pro Schulträger
- mehrere Schemata pro Schule
- Import-/Sync-Vorschau vor Übernahme
- Duplikaterkennung
- Batch-Validierung
- Batch-Verbindungstest bzw. Statusabgleich
- exportierbare Fehlerlisten ohne Secrets
- Massenexport von Credential-Daten für externe Safes als explizite, auditierte Aktion

Eine vollständige Bulk-Import-Implementierung ist nicht zwingend Teil des ersten
Schemaverwaltungs-Auftrags. Datenmodell, Service-Schnitt und UI-Architektur dürfen diese
Erweiterung aber nicht verbauen.

### RLS, Autorisierung und Audit

`schema` bleibt eine tenant-gebundene Tabelle:

- `tenant_id` verweist auf den Schulträger.
- RLS folgt der Tenant-Policy aus ADR-002/ADR-008.
- `schule_id` muss zu demselben Tenant gehören.
- `instanz_id` darf auf eine mandantenübergreifende `svws_instanz` zeigen.

Gateway- und Admin-Autorisierung dürfen nicht aus `instanz_id` abgeleitet werden. Maßgeblich sind
Tenant, Schule, Schema und Rolle/Scope des Aufrufers.

Sicherheitsrelevante Operationen sind auditpflichtig, insbesondere:

- Schema anlegen
- Schema zuordnen
- Schema deaktivieren/reaktivieren
- Schema löschen oder zur Löschung vormerken
- Migration starten
- Import/Export starten
- Credential-Zuordnung oder Credential-Änderung
- Credential-Export
- fehlgeschlagene gefährliche Operationen

Gefährliche Operationen wie Löschen, Migration, Import/Restore und Deaktivieren benötigen
explizite Bestätigung und dürfen nicht als beiläufige Listenaktion umgesetzt werden.

## Konsequenzen

### Positiv

- Das Modell bleibt konsistent mit ADR-011: Eine SVWS-Instanz kann mehrere Schulträger bedienen,
  ohne selbst Tenant-Objekt zu werden.
- Schulen und Schuldatenbanken bleiben sauber mandantenbezogen; RLS kann auf Schema-Ebene greifen.
- Die spätere Gateway-Logik erhält eine klare Routing-Kette: Tenant/Schule/Schema/Instanz.
- Betreiber können später große Bestände über Sync-/Import-Workflows verwalten, ohne jedes Schema
  oder Credential einzeln erfassen zu müssen.
- Standardisierte Schema- und Credential-Namen erleichtern Betrieb, Support, Massenimport,
  Migration und externe Safe-Ablage.
- Die historisch gewachsene SVWS-Privileged-API wird gekapselt statt direkt in die EduGate-UI
  durchgereicht.

### Negativ / Risiken

- Das Modell ist komplexer als eine einfache Liste "Schemata pro Server".
- Unzugeordnete Sync-Funde brauchen einen eigenen Workflow, sonst entsteht Risiko falscher
  Mandantenzuordnung.
- Erweiterbare Umgebungen erhöhen UI- und Validierungsaufwand gegenüber einem kleinen festen Enum.
- Namenskonventionen können lokale Betreiberrealitäten nie vollständig vorwegnehmen; die
  Standardkonvention muss daher konfigurierbar bleiben.
- Bulk-Import und Batch-Validierung müssen sorgfältig gebaut werden, damit keine
  mandantenübergreifenden Zuordnungsfehler entstehen.
- Credential-Export ist betrieblich nötig, erhöht aber das Risiko unbeabsichtigter Offenlegung und
  braucht klare Berechtigungen, Warnungen, kurze Download-Zeitfenster und Audit.
- Gefährliche Privileged-API-Operationen erfordern zusätzliche Schutzmechanismen und Tests.

## Verweise

- ADR-002 (Mandantenmodell und RLS)
- ADR-006 (Secret-Handling für SVWS-Zugangsdaten)
- ADR-009 (Operator-Zugriff und Audit)
- ADR-011 (SVWS-Instanz als geteilte Betriebsressource)
- `docs/entwicklung/svws-server-api.md`
- `examples/open-api-privileged.json`
