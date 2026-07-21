# ADR-020: Schulen als Stammdaten vor Schuldatenbanken

- **Status:** accepted
- **Datum:** 2026-07-21
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate verwaltet Schulträger, Schulen, SVWS-Instanzen und Schuldatenbanken/Schemata. ADR-012
legt bereits fachlich fest, dass ein Schema der Datenbestand einer Schule ist:

```text
Schulträger
  -> Schule
      -> SVWS-Schema / Schuldatenbank
           -> liegt auf SVWS-Instanz
```

In der bisherigen Umsetzung und in einigen UI-Abläufen ist diese Reihenfolge noch nicht konsequent
genug sichtbar. Betreiber legen oder übernehmen teils zuerst eine Schuldatenbank und ordnen sie
dann einem Schulträger zu; die Schule entsteht dabei erst im Kontext der Schuldatenbank. Das ist
fachlich verkehrt herum: Eine Schule ist Stammdatum des Schulträgers. Eine Schuldatenbank ist nur
ein technischer und fachlicher Datenbestand dieser Schule für eine bestimmte Umgebung, z. B.
Produktiv, Test oder Schulung.

Diese Unschärfe wird mit ADR-014 besonders sichtbar. Die SVWS-Privileged-API liefert vorhandene
Schemata als technische Funde. Aus einem Schemanamen wie `123456_test` kann EduGate höchstens eine
Schulnummer und Umgebung vermuten. Daraus darf aber keine automatische Mandanten- oder
Schulzuordnung entstehen. Der Betreiber muss bewusst entscheiden, zu welcher Schule der Fund gehört.

Gleichzeitig existiert eine offizielle API bzw. Datenquelle des Landes für die Liste der Schulen.
EduGate sollte diese Referenzdaten nutzen können, damit Betreiber Schulen nicht unnötig manuell
erfassen müssen. Die Landesliste kann aber nicht die einzige Wahrheit sein: Es braucht Sonderwege
für Schulträger oder Schulen, die noch nicht, nicht mehr oder aus organisatorischen Gründen nicht
passend in der Liste stehen.

## Betrachtete Optionen

1. **Schulen weiter implizit beim Anlegen einer Schuldatenbank erzeugen:** schnell und nah am
   aktuellen Ablauf, aber fachlich unsauber. Die Schuldatenbank würde weiterhin als Quelle der
   Schule erscheinen, obwohl sie eigentlich Kind der Schule ist.
2. **Schule als verpflichtendes Stammdatum vor jedem Schema behandeln:** fachlich sauber und
   kompatibel mit ADR-012. Betreiber wählen beim Anlegen oder Zuordnen einer Schuldatenbank eine
   bereits vorhandene Schule aus.
3. **Offizielle Landesliste direkt als operative Schultabelle verwenden:** vermeidet doppelte
   Datenhaltung, macht EduGate aber abhängig von Verfügbarkeit, Struktur und Vollständigkeit der
   externen Liste und lässt Sonderfälle nur schwer zu.
4. **Landesliste als Referenzkatalog plus eigener Betreiberbestand:** EduGate importiert oder
   synchronisiert die offizielle Schulliste als Katalog. Die operative Tabelle `schule` bleibt der
   Betreiberbestand und kann optional auf einen Katalogeintrag verweisen. Sonderfälle werden
   explizit markiert.

## Entscheidung

Gewählt wird **Option 2 kombiniert mit Option 4**.

SVWS-EduGate behandelt Schulen künftig konsequent als eigenständige Stammdaten unter einem
Schulträger. Schuldatenbanken/Schemata werden erst danach für eine vorhandene Schule angelegt oder
aus SVWS-Funden übernommen. Die offizielle Landes-Schulliste wird als Referenzkatalog genutzt,
ersetzt aber nicht den Betreiberbestand.

### Fachliches Zielmodell

Die fachliche Reihenfolge ist verbindlich:

```text
Schulträger anlegen/auswählen
  -> Schule aus Landesliste übernehmen oder als Sonderfall anlegen
      -> Schuldatenbank/SVWS-Schema planen, anlegen oder zuordnen
```

Ein Schema darf nicht der fachliche Ort sein, an dem eine Schule "nebenbei" entsteht. Beim
Anlegen eines Schemas und beim Zuordnen eines SVWS-Schema-Funds wird eine vorhandene Schule
ausgewählt. Fehlt die Schule, muss sie vorher oder aus dem Workflow heraus bewusst angelegt werden.

### Landes-Schuldatei als Referenzkatalog

EduGate führt eine eigene Referenzstruktur für die offizielle Schuldatei des Landes ein, z. B.
als Tabelle oder später als materialisierte/gespeicherte Katalogprojektion:

```text
schule_katalog
  -> offizielle Schulnummer
  -> offizieller Schulname
  -> Schulform / Schulart, soweit verfügbar
  -> Adresse / Gemeinde / weitere nicht geheime Stammdaten, soweit fachlich sinnvoll
  -> Trägerinformationen, soweit die Quelle sie belastbar liefert
  -> Gültigkeit / Stand / Importzeitpunkt
  -> technische Quellen-ID oder Versionskennung
```

Die genaue Feldliste hängt von der offiziellen API ab und wird bei der Implementierung aus deren
Schema abgeleitet. EduGate speichert nur Daten, die für Betreiberverwaltung, Suche, Auswahl,
Abgleich und Nachvollziehbarkeit erforderlich sind.

Die Verwaltung dieser Schuldatei wird als Betreiber-Einstellung umgesetzt. Unter dem
Einstellungen-Bereich (Zahnrad) erhält EduGate eine Kachel **Schuldatei**. Diese Kachel ist der
erste Einstieg für:

- Stand der zuletzt importierten/synchronisierten Landes-Schuldatei,
- manuellen oder später geplanten Abruf aus der offiziellen API,
- Suche/Prüfung von Katalogeinträgen,
- Anzeige von Importfehlern oder Abweichungen,
- spätere Konfiguration der Quelle, falls mehrere Umgebungen oder Versionen unterstützt werden.

Die Schuldatei-Kachel ist kein Ort zum Verwalten von Schuldatenbanken. Sie verwaltet nur den
Referenzkatalog, aus dem operative Schulen übernommen oder geprüft werden können.

### Operative Schulen bleiben Betreiberbestand

Die bestehende operative Entität `schule` bleibt erhalten und repräsentiert den Bestand des
Betreibers unter einem Schulträger. Sie erhält perspektivisch Metadaten zur Herkunft, z. B.:

```text
schule
  -> schultraeger_id / tenant_id
  -> schulnummer
  -> name
  -> optional katalog_id
  -> quelle: LANDESLISTE | MANUELL | SONDERFALL
  -> sonderfall_hinweis / begründung
  -> aktiv / historisch, soweit benötigt
```

Ein Katalogeintrag kann vorgeschlagen oder übernommen werden. Die operative Schule ist danach aber
ein eigener EduGate-Datensatz, der unter einem konkreten Schulträger steht. So bleibt EduGate
stabil, auch wenn die Landesliste nicht erreichbar ist, sich ändert oder Sonderfälle nicht abbildet.

### Sonderfälle

Sonderfälle sind ausdrücklich erlaubt und werden nicht als Fehlerzustand behandelt. Sie müssen aber
sichtbar und nachvollziehbar sein.

Sonderfälle können z. B. sein:

- Schulträger, die in der offiziellen Liste nicht passend vorhanden sind,
- Schulen ohne belastbaren Katalogtreffer,
- neue, umbenannte, auslaufende oder organisatorisch besondere Schulen,
- Übergangs- oder Migrationsfälle,
- technische Test-/Schulungsbestände, die bewusst nicht aus der Landesliste stammen.

Für Sonderfälle gelten Mindestregeln:

- Betreiber muss sie bewusst als Sonderfall erfassen.
- Ein Hinweis oder eine Begründung soll hinterlegt werden können.
- UI-Listen markieren Sonderfälle sichtbar.
- Zuordnung eines Schemas zu einer Sonderfall-Schule ist erlaubt, aber auditierbar.
- Sonderfälle dürfen spätere Katalogtreffer bekommen und dann kontrolliert verknüpft werden.

### Auswirkungen auf Schema- und Fund-Workflows

Beim Anlegen einer neuen Schuldatenbank gilt künftig:

1. Schulträger auswählen.
2. Schule aus den Schulen dieses Schulträgers auswählen.
3. Falls die Schule fehlt: Schule aus Landesliste übernehmen oder Sonderfall-Schule anlegen.
4. SVWS-Instanz auswählen.
5. Umgebung auswählen.
6. Schemanamen vorschlagen lassen oder bewusst abweichend erfassen.
7. Schema als EduGate-Planungsdatensatz anlegen; echte SVWS-Anlage bleibt ein geschützter
   ADR-014-Workflow.

Beim Zuordnen eines unzugeordneten SVWS-Schema-Funds gilt künftig:

1. Fund öffnen, z. B. aus der SVWS-Instanzliste.
2. Technische Funddaten anzeigen: Instanz, Schemaname, Benutzername, Revision, Config-/Deaktiviert-/
   Tainted-Flags.
3. Falls verfügbar, zusätzliche Schulinfo aus der SVWS-Privileged-API anzeigen, z. B.
   `GET /api/schema/liste/info/{schema}/schule`.
4. Schulträger auswählen.
5. Bestehende Schule dieses Schulträgers auswählen.
6. Falls die Schule fehlt: Schule aus Landesliste übernehmen oder Sonderfall-Schule anlegen.
7. Umgebung explizit auswählen oder korrigieren. Die SVWS-API liefert keine EduGate-Umgebung.
8. Zuordnung bestätigen; danach wird ein EduGate-`schema`-Datensatz für diese Schule erzeugt oder
   ein passender vorhandener Datensatz aktualisiert.

Die SVWS-Schulinfo darf dabei nur Orientierung bieten. Sie kann Vorschläge unterstützen, aber keine
automatische Tenant- oder Schulzuordnung auslösen.

### UI-Leitlinien

- Schulträger-Detailansichten zeigen Schulen als eigene Stammdatenliste.
- Schuldatenbank-Workflows wählen eine Schule aus, statt sie implizit durch das Schema zu erzeugen.
- Die Schuldatenbanken-Liste zeigt nur Schemata, die einer EduGate-Schule zugeordnet sind.
- SVWS-Funde ohne Zuordnung bleiben in der Instanzsicht sichtbar und führen über einen
  Review-/Zuordnungsworkflow in das operative Modell.
- Die Einstellungen-Kachel **Schuldatei** wird als erster Schritt umgesetzt, bevor umfangreiche
  neue Schema-Workflows gebaut werden. Sie schafft die Grundlage für Suche, Übernahme und
  Sonderfallmarkierung.

## Konsequenzen

### Positiv

- Das Datenmodell folgt konsequent ADR-012: Schule vor Schema.
- Die spätere Zuordnung von SVWS-Funden wird fachlich sauberer und weniger fehleranfällig.
- Die offizielle Landes-Schuldatei verbessert Suche, Plausibilisierung und Stammdatenqualität.
- Sonderfälle bleiben möglich, aber sichtbar und auditierbar.
- Betreiber können Schulen schon vorbereiten, bevor eine SVWS-Datenbank existiert.

### Negativ / Risiken

- Mehr Modell- und UI-Aufwand als ein einfaches Schema-Anlegen-Formular.
- Es braucht eine saubere Katalog-/Importlogik für die Landes-Schuldatei.
- Sonderfälle dürfen nicht zu einer zweiten, ungepflegten Parallelwelt werden; sie brauchen klare
  Markierung, Hinweise und spätere Verknüpfbarkeit.
- Bestehende Workflows, die Schule und Schema zu eng koppeln, müssen angepasst werden.

### Offene Punkte

- Migrationspfad für bestehende EduGate-Schulen und Schemata.
- ~~Konkrete technische Anbindung der offiziellen Landes-API~~, ~~exakte Feldliste des
  Katalogs~~ und ~~Detailentscheidung zum Schulträger-Katalog~~: geklärt, siehe Nachtrag
  „Grundlagenrecherche vor Umsetzung" unten.

## Nachtrag (Grundlagenrecherche vor Umsetzung): Schulträger-Katalog, Sonderfall-Symmetrie, Feldliste

Vor Beginn der Umsetzung (Kachel „Schuldatei", GitHub-Issue #28) wurden die realen NRW-Endpunkte
(`.../export/json/konten`, `.../export/json/katalog`) inspiziert sowie die bereits vorhandene
Implementierung im Projekt `SVWS-Main-Server` (`de.schultraeger.*`) gesichtet. Das klärt die zuvor
offenen Punkte dieses ADRs.

### Technischer Befund

- `konten` liefert 8.635 `organisationseinheit`-Datensätze mit Unterscheidungsfeld `oeart`.
  Relevant für EduGate: `oeart=1` → Schule (5.703 Datensätze), `oeart=2` → Schulträger (1.426
  Datensätze). Schulträger sind damit **vollwertige, eigenständige Organisationseinheiten** mit
  eigener Adresse und eigenem Gültigkeitszeitraum, strukturell gleichwertig zu Schulen – kein
  bloßes Unterfeld einer Schule. Die übrigen 13 `oeart`-Werte (Schulaufsichtsbehörde,
  Studienseminare, Testschulen, Kompetenzteams usw.) sind für EduGate nicht relevant und werden
  beim Import verworfen.
- Jede Schule referenziert ihren Träger eindeutig über `grunddaten.schultraegernummer`, das exakt
  auf das Feld `schulnummer` des zugehörigen `oeart=2`-Datensatzes verweist (stichprobenartig zu
  100 % verifiziert).
- `katalog` liefert Code-Kataloge zur Übersetzung von Kürzeln, u. a. `OrganisationseinheitArt`
  (löst `oeart` auf) und `Traeger` (löst die **Trägerschaftsart** auf, z. B. „kreisfreie Stadt",
  „Erzbistum" – keine Liste konkreter, benannter Schulträger-Organisationen).
- `SVWS-Main-Server` filtert aktuell **nicht** nach `oeart`: Jeder `organisationseinheit`-Datensatz
  mit gesetzter `schulnummer` landet undifferenziert in einer flachen `nrw_schulkatalog`-Tabelle –
  Schulen, Schulträger und Schulaufsichtsbehörden vermischt. Zusätzlich ersetzt der
  Refresh-Vorgang dort bei jedem Lauf die komplette Tabelle (`clearAll()` + `saveAll()`) und
  vergibt je Zeile eine neue zufällige UUID. EduGate übernimmt aus dieser Implementierung nur das
  Datenquellen-Wissen (Endpunkte, Feldnamen, Join-Schlüssel), nicht die Tabellen- oder
  Refresh-Struktur.

### Entscheidung

**1. Eigener `schultraeger_katalog`, symmetrisch zu `schule_katalog`.** Da Schulträger in der
Quelle vollwertige, eigenständige Organisationseinheiten sind, führt EduGate einen eigenen
Referenzkatalog `schultraeger_katalog` (befüllt aus `oeart=2`) neben `schule_katalog` (befüllt aus
`oeart=1`) ein, statt Trägerdaten nur denormalisiert in `schule_katalog` mitzuführen.
`schule_katalog.schultraegernummer` bleibt die fachliche Verknüpfung zu
`schultraeger_katalog.traegernummer` – bewusst ohne erzwingende Fremdschlüssel-Constraint, damit
eine Schule auch importierbar bleibt, falls der zugehörige Trägerdatensatz zum Importzeitpunkt
fehlt oder verworfen wurde.

**2. Sonderfall-/Herkunftsfelder gelten symmetrisch für `schultraeger` und `schule`.** Die
operative Tabelle `schultraeger` erhält dieselben Felder, die dieses ADR für `schule` vorsieht:
optionaler `katalog_id`-Verweis, `quelle: LANDESLISTE | MANUELL | SONDERFALL`,
`sonderfall_hinweis`. Sonderfall-Schulträger (z. B. Träger, die in der Landesliste nicht passend
vorhanden sind) werden damit genauso auditierbar erfasst wie Sonderfall-Schulen.

**3. Stabile Identität von Katalogeinträgen über Refreshs hinweg.** Anders als in
`SVWS-Main-Server` (kompletter Tabellen-Neuaufbau mit zufälligen UUIDs je Refresh) müssen
`schule_katalog`- und `schultraeger_katalog`-Zeilen über mehrere Refreshs hinweg dieselbe Identität
behalten, weil operative Datensätze über `katalog_id` auf sie verweisen können. Der Import
arbeitet daher als Upsert über den fachlichen Schlüssel (`bundeslandkennung` + Quellen-`schulnummer`
bzw. -`traegernummer`), nicht als Clear-and-Insert. Datensätze, die ein Refresh nicht mehr liefert,
werden nicht gelöscht, sondern als nicht mehr aktuell markiert (Abgleich mit `aufloesung` bzw.
einem `zuletzt_gesehen_am`-Zeitstempel), damit bestehende `katalog_id`-Verweise nicht ins Leere
laufen.

**4. Refresh bleibt eine geschützte, auditierte Operator-Aktion.** Anders als der öffentliche
(`@PermitAll`) Refresh-Endpunkt in `SVWS-Main-Server` läuft der EduGate-Refresh über
`OperatorAccess` (ADR-009), ausgelöst über die Schuldatei-Kachel – kein automatisiert-öffentlicher
Zugriff.

### Konkretisierte Feldliste

`schule_katalog` (aus `oeart=1`):

```text
bundeslandkennung, schulnummer (Quellen-ID)
schulname (grunddaten.kurzbezeichnung)
schultraegernummer (Verknüpfung zu schultraeger_katalog.traegernummer)
schulform/-art (grunddaten.schulform, ggf. mehrere zeitlich gültige Einträge)
strasse, plz, ort (aktuell gültige Hauptstandortadresse aus adressen)
kreis (adressen.regionalschluessel, erste 5 Stellen)
telefon, fax, email, homepage (aus erreichbarkeiten, soweit vorhanden)
aufloesung ("31.12.9999" = aktuell aktiv)
zuletzt_gesehen_am, quellen_stand (Importzeitpunkt/-version)
```

`schultraeger_katalog` (aus `oeart=2`, gleiches Grundmuster):

```text
bundeslandkennung, traegernummer (= Quellen-"schulnummer" des Trägerdatensatzes)
traegername (grunddaten.kurzbezeichnung bzw. amtsbez1-3)
traegerschaftsart (aus Katalog "Traeger", z. B. "kreisfreie Stadt")
strasse, plz, ort
aufloesung, zuletzt_gesehen_am, quellen_stand
```

Das Feld heißt bewusst `traegerschaftsart`, nicht `schulamt` wie in `SVWS-Main-Server` – dort wird
die Trägerschaftsart fälschlich unter dem Feldnamen `schulamt` geführt, was mit einer
Schulaufsichtsbehörde verwechselt werden kann.

### Status

Mit diesem Nachtrag wird der Status auf **accepted** gehoben.

## Verweise

- ADR-011 (SVWS-Instanz als geteilte Betriebsressource)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-013 (Betreiber-UI für Schemaverwaltung und Schuldatenbanken)
- ADR-014 (Verwendung echter Aufrufe der SVWS-Privileged-API)
- `docs/entwicklung/svws-server-api.md`
- GitHub-Issue #28 (Schuldatei: öffentliche Endpunkte auswerten und Schulträger-Katalog bereitstellen)
