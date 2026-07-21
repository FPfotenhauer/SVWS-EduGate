# ADR-020: Schulen als Stammdaten vor Schuldatenbanken

- **Status:** proposed
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

- Konkrete technische Anbindung der offiziellen Landes-API: Authentifizierung, Rate Limits,
  Datenformat, Aktualisierungsintervall und Fehlerstrategie.
- Exakte Feldliste des Katalogs nach Sichtung der API.
- Migrationspfad für bestehende EduGate-Schulen und Schemata.
- Detailentscheidung, ob Sonderfall-Schulträger ebenfalls einen eigenen Katalog bekommen oder
  zunächst nur als operative Schulträger ohne Katalogverweis geführt werden.

## Verweise

- ADR-011 (SVWS-Instanz als geteilte Betriebsressource)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-013 (Betreiber-UI für Schemaverwaltung und Schuldatenbanken)
- ADR-014 (Verwendung echter Aufrufe der SVWS-Privileged-API)
- `docs/entwicklung/svws-server-api.md`
