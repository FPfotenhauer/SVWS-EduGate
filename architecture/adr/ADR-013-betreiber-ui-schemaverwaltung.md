# ADR-013: Betreiber-UI für Schemaverwaltung und Schuldatenbanken

- **Status:** accepted
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

ADR-012 definiert das fachliche Modell für Schuldatenbanken/Schemata: Ein Schema gehört zu
einer Schule und damit zu genau einem Schulträger, liegt technisch aber auf einer
mandantenübergreifenden SVWS-Instanz. Die erste Umsetzung der Schemaverwaltung stellte Schulen
und deren Schemata unter "Schulträger bearbeiten" dar. Das bildet zwar die fachliche
Mandantenzuordnung ab, ist für Betreiber aber die falsche Arbeitsoberfläche: Betreiber müssen
vor allem sehen, welche SVWS-Instanzen welche Schemata hosten, welche Schulen dort laufen, welche
Umgebungen und Status betroffen sind und welche Betriebsaktionen sicher anstehen.

SVWS-EduGate richtet sich nicht an sehr kleine Schulträger, die nur wenige Schemata direkt am
SVWS-Server administrieren. Dafür existiert im SVWS-Server bereits ein Admin-Client unter
`https://localhost:8443/admin`. SVWS-EduGate muss eine größere Betreiberrealität abbilden:
mehrere Schulträger, viele Schulen, mehrere SVWS-Instanzen, Migrationsprozesse, Zertifikate,
Credential-/API-Zugriffe, Massenübersichten, Fehlerlisten und auditierte gefährliche
Operationen.

Typische Betreiber-Use-Cases sind:

- SVWS-Instanzen mit allen darauf gehosteten Schuldatenbanken überblicken.
- Neue Schulen und dazu passende Produktiv-/Test-/Schulungs-Schemata anlegen.
- Daten aus alter Software in ein neues Schema migrieren; der Betreiber wählt dabei aus den von
  der SVWS-Privileged-API unterstützten Altformaten, z. B. mehreren Datenbankformaten aus dem
  Bestandssystemumfeld.
- Unzugeordnete oder importierte Schemata einer Schule zuordnen, ohne Tenant-Zuordnung zu raten.
- Selbstsignierte Zertifikate für einen SVWS-Server erzeugen oder eigene Zertifikate importieren,
  wenn die SVWS-Privileged-API diese Funktionen bereitstellt.
- Schema-bezogene API-Zugriffe verwalten: Heute benötigt jedes Schul-Schema einen Benutzer mit
  passenden Berechtigungen für die External API (aktuell BasicAuth); perspektivisch soll dies auf
  API-Token umgestellt werden.

Ohne eigenes UI-Konzept droht die Schemaverwaltung zu einer Sammlung verschachtelter CRUD-Formen
zu werden. Das wäre für Betreiber langsam, unübersichtlich und fehlerträchtig.

## Betrachtete Optionen

1. **Schulen und Schemata weiter in "Schulträger bearbeiten" einbetten:** fachlich naheliegend,
   weil Schule und Schema tenant-gebunden sind, aber für Betreiber maximal ungünstig. Die
   Instanzsicht, Massenverwaltung, Migration, Zertifikate und Credential-Zustände verschwinden in
   einem Formular, das eigentlich nur Stammdaten eines Schulträgers bearbeiten sollte.
2. **Eigene Betreiber-UI für SVWS-Instanzen und Schuldatenbanken:** eine eigenständige
   Arbeitsoberfläche, die Instanzen, Schemata, Schulen, Schulträger, Umgebungen, Status,
   Migrationen, Zertifikate und Credentials gemeinsam sichtbar macht. Schulträger- und
   Schulansichten bleiben per Querverweis erreichbar, sind aber nicht der Hauptort für
   Schemaverwaltung.
3. **SVWS-Admin-Client verlinken oder einbetten:** für kleine Installationen plausibel, aber für
   EduGate ungeeignet. Der SVWS-Admin-Client ist nicht auf große Betreiberumgebungen,
   Mandantenübersichten, Credential-Massenverwaltung, EduGate-Audit und spätere
   API-Token-Übergänge ausgelegt.

## Entscheidung

Gewählt wurde **Option 2**.

SVWS-EduGate erhält für ADR-012 keine verschachtelte Nebenansicht unter "Schulträger
bearbeiten", sondern eine eigene Betreiber-UI für **SVWS-Instanzen und Schuldatenbanken**. Diese
Oberfläche ist der primäre Einstieg für alle schema- und instanzbezogenen Betriebsabläufe.

### Navigations- und Informationsarchitektur

Die Schemaverwaltung wird als eigener Hauptbereich der Administration geführt. Dieser Bereich hat
zwei gleichwertige Hauptsichten:

1. **SVWS-Instanzen:** Serverperspektive. Betreiber sehen pro Instanz, welche Schuldatenbanken auf
   diesem Server liegen, welche Status-/Zertifikats-/Credential-Auffälligkeiten es gibt und welche
   Betriebsaktionen für diese Instanz anstehen.
2. **Schuldatenbanken:** fachliche Schul-/Schema-Perspektive. Betreiber sehen, welche Schulen mit
   welchen Datenbanken existieren, gruppierbar nach Schulträger und filter-/gruppierbar nach
   Umgebungsvariante wie Produktiv, Test, Schulung, Migration oder Archiv.

Der genaue Menüzuschnitt ist eine UI-Detailentscheidung; möglich sind z. B. zwei Menüpunkte
"SVWS-Instanzen" und "Schuldatenbanken" oder ein gemeinsamer Bereich "SVWS-Betrieb" mit zwei Tabs.
Verbindlich ist:

- Schulen und Schemata dürfen nicht primär unter "Schulträger bearbeiten" versteckt werden.
- Die UI muss eine Instanzsicht anbieten: SVWS-Instanz -> gehostete Schuldatenbanken -> Schule ->
  Schulträger.
- Die UI muss eine fachliche Sicht anbieten: Schulträger/Schule -> Schuldatenbanken ->
  SVWS-Instanz.
- Beide Sichten müssen dieselben Daten zeigen und per Querverweis ineinander navigierbar sein.
- Navigation zwischen den Sichten muss den Kontext erhalten: Wer aus einer Schuldatenbank zur
  hostenden SVWS-Instanz springt, soll dort genau diese Schuldatenbank wiederfinden; wer aus einer
  Instanz zu einer Schule oder Schuldatenbank springt, soll nicht in einem allgemeinen
  Schulträger-Bearbeitungsformular landen.
- Listen müssen für große Betreiberumgebungen filter-, such- und sortierbar sein.

Der Betreiber muss auf einen Blick erkennen können:

- welche SVWS-Instanzen existieren und erreichbar sind,
- welche Schemata auf welcher Instanz liegen,
- zu welcher Schule und welchem Schulträger ein Schema gehört,
- welche Umgebung ein Schema hat (Produktiv, Test, Schulung, Migration, Archiv usw.),
- welchen Status ein Schema hat,
- ob Verbindungstest, Sync, Migration, Zertifikat oder Credential-Zustand Aufmerksamkeit brauchen.

### Instanzsicht

Die Instanzsicht ist kein technischer Rohdump der SVWS-API. Sie ist eine Betreiberübersicht mit
verdichteten Betriebsinformationen:

- Kurzbezeichnung, Base-URL, Status und letzter Verbindungstest der SVWS-Instanz.
- Zertifikatsstatus: unbekannt, selbstsigniert, importiert, ablaufend, ungültig oder zu prüfen.
- Credential-Status der privilegierten Instanz-Credentials ohne Klartextanzeige.
- Anzahl der gehosteten Schemata nach Umgebung und Status.
- Auffällige Schemata: Migration erforderlich, Fehler, deaktiviert, unzugeordnet,
  Credential-Problem.
- Aktionen: Verbindung testen, Schemata synchronisieren, Zertifikat verwalten,
  privilegierte Credentials prüfen/ersetzen, gehostete Schuldatenbanken anzeigen.

Die Instanzsicht darf trotz sichtbarer Schuldatenbanken nicht zu einer unübersichtlichen
Megatabelle werden. Sie zeigt standardmäßig verdichtete Kennzahlen, Statusgruppen und auffällige
Schemata; die vollständige Liste der gehosteten Schuldatenbanken wird über Filter, Suche,
aufklappbare Bereiche oder eine Instanz-Detailansicht zugänglich. Betreiber müssen zwischen
"Serverzustand verstehen" und "konkrete Schuldatenbank bearbeiten" wechseln können, ohne die
Orientierung zu verlieren.

### Schuldatenbank- und Schulsicht

Schulen sind weiterhin fachlich zentrale Objekte, aber nicht der Container, in dem die ganze
Schemaverwaltung versteckt wird. Eine Schule erhält eine kompakte Schuldatenbank-Übersicht:

- Produktivschema, falls vorhanden.
- Nicht-Produktiv-Schemata wie Test, Schulung, Migration, Archiv.
- Zielinstanz je Schema.
- Status, letzter Abgleich, letzter Verbindungstest.
- Credential-/API-Zugriffszustand.
- direkte Links in die Instanzsicht und in Detail-/Workflow-Dialoge.

Die fachliche Schuldatenbank-Sicht muss größere Bestände nach Schulträger, Schule und Umgebung
organisieren können. Eine Schule kann mehrere Datenbanken haben; deshalb darf die UI nicht so tun,
als gäbe es genau eine Datenbank pro Schule. Betreiber müssen z. B. alle Produktivdatenbanken
eines Schulträgers, alle Testdatenbanken auf einer Instanz oder alle Schemas mit
Migrations-/Credential-Problem filtern können.

Die Aktion "Neue Schule anlegen" darf einen Schuldatenbank-Schritt enthalten, soll aber als
geführter Workflow umgesetzt werden: Schulträger auswählen, Schuldaten erfassen, Zielinstanz
wählen, Schema-Namenskonvention anwenden oder Sonderpfad dokumentieren, Umgebung festlegen,
Credential-/API-Zugriff vorbereiten und optional Schema auf dem SVWS-Server anlegen.

### Migration aus alter Software

Migration ist ein eigener, geführter Betreiber-Workflow und keine beiläufige Listenaktion.

Der Workflow muss mindestens vorsehen:

- Zielschule und Ziel-Schema eindeutig auswählen oder im Workflow neu anlegen.
- Altformat aus den von der SVWS-Privileged-API unterstützten Formaten auswählen; die UI darf
  die konkrete Formatliste nicht hart annehmen, sondern muss sie aus Konfiguration/API ableiten
  oder zentral pflegen.
- Quelle erfassen, z. B. Upload, Serverpfad oder ein später zu definierender Importkanal.
- Vorprüfung/Validierung mit sichtbarer Fehlerliste.
- klare Trennung zwischen Testlauf/Vorschau und endgültiger Migration.
- explizite Bestätigung für überschreibende oder produktionsnahe Operationen.
- Audit aller Schritte ohne Secrets und ohne unnötige personenbezogene Daten.

Die UI muss deutlich machen, ob eine Migration ein neues Schema befüllt, ein Testschema nutzt
oder ein bestehendes Schema verändert. Produktivveränderungen dürfen nicht wie normale
Bearbeiten-Buttons wirken.

### Zertifikatsverwaltung

Wenn die SVWS-Privileged-API Endpunkte für Zertifikate anbietet, wird deren Nutzung in EduGate als
Instanz-Betriebsworkflow geplant:

- selbstsigniertes Zertifikat für eine SVWS-Instanz erzeugen,
- eigenes Zertifikat importieren,
- Zertifikatsstatus und Ablaufdatum anzeigen, soweit technisch verfügbar,
- Warnungen bei selbstsignierten, ablaufenden oder ungültigen Zertifikaten anzeigen,
- Zertifikatswechsel auditieren,
- keine privaten Schlüssel oder Zertifikatsgeheimnisse in Logs, Auditdetails oder allgemeinen
  API-Antworten offenlegen.

Die Zertifikatsverwaltung gehört zur Instanzsicht, nicht zur Schulträger-Bearbeitung.

### Credential- und API-Zugriffsverwaltung

SVWS-EduGate muss Credential-Verwaltung als eigene Betreiberfähigkeit behandeln. Der aktuelle
SVWS-Stand, in dem pro Schul-Schema ein Benutzer mit Berechtigungen für die External API
angelegt wird und der Zugriff per BasicAuth erfolgt, ist nur der Startpunkt. Die UI und das Modell
müssen eine spätere Umstellung auf API-Token aufnehmen können.

Verbindliche UI-/Modellprinzipien:

- Credential-/API-Zugriff wird schema-bezogen angezeigt, nicht als lose technische Notiz.
- Die UI zeigt Credential-Metadaten und Status, aber niemals Klartext-Secrets.
- BasicAuth-Benutzer und spätere API-Token werden als Varianten eines abstrakten
  API-Zugriffs-/Credential-Konzepts behandelt.
- Betreiber müssen erkennen können, ob für ein Schema ein External-API-Zugriff existiert,
  getestet wurde, abgelaufen/ungültig ist oder Rotation braucht.
- Aktionen wie Erzeugen, Ersetzen, Rotieren, Deaktivieren, Exportieren oder Anzeigen eines
  Einmal-Secrets sind explizit, berechtigt, bestätigt und auditiert.
- Exportpfade müssen mit ADR-012 kompatibel bleiben: nutzbar für externe Safes, kurze
  Download-Zeitfenster, keine Secrets im Audit.

Die Umstellung von BasicAuth auf API-Token darf später nicht zu einer zweiten, parallelen UI
führen. ADR-013 fordert deshalb eine UI-Abstraktion "API-Zugriff" statt eine fest verdrahtete
"BasicAuth-Benutzer"-Oberfläche.

### Unzugeordnete Schemata und Synchronisation

Die Instanzsicht muss Schemata anzeigen können, die auf einer SVWS-Instanz gefunden werden, aber
noch keiner Schule zugeordnet sind. Diese Funde erhalten einen eigenen Review-Workflow:

- Fund sichtbar machen,
- technische Herkunft und Instanz anzeigen,
- mögliche Schule/Schulträger bewusst auswählen,
- keine automatische Tenant-Zuordnung allein aus dem Schemanamen,
- Zuordnung auditieren,
- Konflikte und Dubletten sichtbar machen.

### Schutz für gefährliche Aktionen

Folgende Aktionen dürfen nicht als einfache Inline-Buttons ohne Schutz umgesetzt werden:

- Schema löschen oder zur Löschung vormerken,
- Schema deaktivieren/reaktivieren,
- Migration starten,
- Import/Restore starten,
- Credential rotieren oder exportieren,
- Zertifikat ersetzen,
- produktionsnahes Schema überschreiben.

Sie benötigen mindestens klare Kontextanzeige, Folgenbeschreibung, Berechtigungsprüfung,
explizite Bestätigung und Audit. Für besonders riskante Aktionen kann eine zweite Bestätigung,
Vier-Augen-Prinzip oder ein späteres Freigabemodell vorgesehen werden.

### Abgrenzung

Dieses ADR legt das UI- und Workflow-Zielbild für Schemaverwaltung fest. Es entscheidet noch
nicht das vollständige Backup-/Restore-Konzept für Schulschemata; dieses wird bei Bedarf in
einem eigenen ADR behandelt. Restore-nahe UI-Aktionen werden hier nur insoweit beschrieben, wie
sie als gefährliche Betreiberoperationen in der UI geschützt werden müssen.

## Konsequenzen

### Positiv

- Die UI folgt dem tatsächlichen Betreiberalltag: Instanzen, gehostete Schemata, Status,
  Migration, Zertifikate und API-Zugriffe sind zusammen sichtbar.
- Die falsche Tendenz, Schulen und Schemata tief in "Schulträger bearbeiten" zu verstecken, wird
  architektonisch korrigiert.
- Große Betreiberumgebungen erhalten eine massentaugliche Arbeitsoberfläche statt vieler
  verschachtelter Einzelmasken.
- Der aktuelle BasicAuth-Stand und die spätere API-Token-Zielrichtung können in einer gemeinsamen
  Credential-/API-Zugriffsoberfläche zusammengeführt werden.
- Gefährliche SVWS-Privileged-API-Operationen werden von Anfang an als eigene Workflows mit
  Schutzmechanismen, Status und Audit gedacht.

### Negativ / Risiken

- Die UI wird komplexer als eine einfache CRUD-Verwaltung unter Schulträger oder Schule.
- Eine gute Instanz-/Schema-Übersicht erfordert Backend-Projektionen oder Query-Modelle, die
  Daten aus SVWS-Instanz, Schulträger, Schule, Schema, Credential-Status, Zertifikat und Audit
  sinnvoll verdichten.
- Migration, Zertifikate und Credential-Verwaltung haben unterschiedliche Fehler- und
  Sicherheitsmodelle; sie dürfen in der UI nicht vermischt oder verharmlost werden.
- Die spätere Umstellung von BasicAuth auf API-Token muss vorbereitet, aber nicht spekulativ
  überimplementiert werden.

## Verweise

- ADR-006 (Secret-Handling für SVWS-Zugangsdaten)
- ADR-009 (Operator-Zugriff und Audit)
- ADR-011 (SVWS-Instanz als geteilte Betriebsressource)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- `docs/entwicklung/svws-server-api.md`
- `examples/open-api-privileged.json`
