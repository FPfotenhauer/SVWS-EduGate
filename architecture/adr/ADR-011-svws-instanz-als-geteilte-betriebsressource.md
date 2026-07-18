# ADR-011: SVWS-Instanz als geteilte, mandantenübergreifende Betriebsressource

- **Status:** accepted
- **Datum:** 2026-07-17
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Der zweite vertikale Durchstich implementiert die **SVWS-Serververwaltung**. Der bisherige Stand
(V1-Migration, erster Durchstich Schulträgerverwaltung) modellierte `svws_instanz` bereits als
gewöhnliche mandantenbezogene Fachtabelle: `tenant_id uuid NOT NULL REFERENCES schultraeger(id)`
plus generische Tenant-RLS-Policy (ADR-002). Das ist fachlich falsch und wird mit diesem ADR
korrigiert, bevor eine API darauf aufsetzt.

Ein SVWS-Server-Prozess (eine "Instanz") ist eine **technische Betriebsressource** des
Dienstleisters: Er läuft in dessen Rechenzentrum, wird vom Dienstleister betrieben und gewartet,
und mehrere Schulträger können auf **derselben** Instanz jeweils eigene MariaDB-Schemas erhalten
(mehrere hundert Schulen mehrerer Schulträger lassen sich aus Betriebsgründen nicht sinnvoll auf
je eine exklusive Instanz pro Schulträger verteilen). Eine 1:1- oder 1:n-Bindung
`schultraeger → svws_instanz` unterstellt fälschlich, dass eine Instanz genau einem Schulträger
"gehört". Das widerspricht dem Betriebsmodell und würde spätere Schema-/Routing-Entscheidungen
(wer darf welches Schema auf welcher Instanz ansprechen) auf die falsche Ebene heben.

ARCHITECTURE.md Kap. 3, 5 und 7 sowie die V1-Migration gehen an mehreren Stellen implizit von
einer Zuordnung Schulträger → (eigene) Instanz(en) aus. Dieses ADR präzisiert das Mandantenmodell
und wird an diesen Stellen nachgezogen (siehe „Konsequenzen für bestehende Dokumente" unten).

## Betrachtete Optionen

1. **`tenant_id` auf `svws_instanz` beibehalten** (Status quo V1): einfach, aber fachlich falsch
   – zwingt eine Instanz auf genau einen Schulträger, obwohl der Betrieb geteilte Instanzen
   vorsieht. Eine spätere Öffnung für mehrere Schulträger bräuchte eine n:m-Tabelle und einen
   Bruch mit dem bereits ausgelieferten Schema.
2. **`svws_instanz` als mandantenübergreifende Betriebsressource ohne `tenant_id`,
   Mandantenzuordnung ausschließlich über `schema.instanz_id`:** Eine Instanz existiert
   unabhängig von Schulträgern; welcher Schulträger welches Schema auf welcher Instanz nutzt,
   ergibt sich erst aus der (in einem späteren Auftrag umzusetzenden) Schema-Zuordnung
   (`schema.tenant_id`, `schema.schule_id`, `schema.instanz_id` – `schema.instanz_id` existiert
   bereits seit V1 und bleibt unverändert).
3. **n:m-Zuordnungstabelle `schultraeger_svws_instanz` von Anfang an:** bildet "mehrere
   Schulträger je Instanz" ab, führt aber eine zusätzliche, zum jetzigen Zeitpunkt spekulative
   Tabelle ein, obwohl `schema.instanz_id` (Schule → Schema → Instanz) die tatsächlich benötigte
   Zuordnung bereits abbildet. Eine explizite n:m-Instanz-Trägerzuordnung hätte keinen eigenen
   Verwendungszweck, den die Schema-Zuordnung nicht schon erfüllt, und wäre ein Fall von
   spekulativer Generalität.

## Entscheidung

Gewählt wurde **Option 2**:

- `svws_instanz` erhält **keine** `tenant_id`-Spalte und ist **keine** Mandanten-Wurzel und
  **kein** Mandanten-Kind. Sie ist eine mandantenübergreifende Betriebsressource, exakt wie
  `schultraeger` (ADR-008) selbst mandantenübergreifend ist – mit dem Unterschied, dass
  `svws_instanz` nicht einmal eine eigene "Wurzel-Policy auf die eigene id" braucht, weil ihre
  `id` keine Tenant-ID ist und niemals eine sein wird.
- Felder (mindestens): `id`, `name`, `base_url`, `status` (`OK` | `DEGRADED` | `UNREACHABLE`),
  `credentials_encrypted`, `aktiv`, `created_at`, `updated_at`. `name` und `base_url` dürfen nicht
  leer sein; `base_url` ist eindeutig (`UNIQUE`).
- Die künftige Mandantenzuordnung entsteht ausschließlich über die Schema-Ebene:
  `schema.tenant_id`, `schema.schule_id`, `schema.instanz_id` (letztere verweist weiterhin auf
  `svws_instanz.id`, unverändert seit V1). Eine Instanz "gehört" also nie einem Schulträger –
  ein Schulträger nutzt über seine Schulen und deren Schemas bestimmte Instanzen mit, ggf.
  gemeinsam mit anderen Schulträgern.
- **Das Gateway darf Zugriff niemals allein aus der Instanz ableiten.** Späteres Routing
  (Auftrag: API-Gateway) muss für jeden Request immer die vollständige Kette prüfen:
  Tenant/Schulträger → Schule → Schema → Instanz → Berechtigung des API-Clients. Die Instanz ist
  in dieser Kette nur ein technisches Ziel, kein Autorisierungsmerkmal.
- **Konsequenz für RLS** (präzisiert gegenüber der generischen Regel aus ADR-002/ADR-008):
  - `svws_instanz` fällt **nicht** unter die generische Regel "jede Tabelle mit `tenant_id` braucht
    Tenant-RLS" – sie hat schlicht keine `tenant_id`-Spalte, weil sie fachlich kein
    Mandanten-Objekt ist.
  - `svws_instanz` erhält dennoch RLS `ENABLE` **und** `FORCE` (Verteidigungslinie bleibt aktiv),
    aber mit einem eigenen Policy-Muster statt der Tenant-Policy:
    - `edugate_operator` (OperatorAccess, ADR-009) erhält weiterhin die volle
      `FOR ALL USING (true) WITH CHECK (true)`-Policy aus V1 (unverändert) – **alle**
      schreibenden CRUD-Operationen der Control-Plane-API laufen über diesen auditierten Pfad,
      analog zu `schultraeger`.
    - `edugate_gateway_ro` erhält eine eigene, mandantenübergreifende `FOR SELECT USING (true)`-
      Policy: Das Gateway muss jede Instanz lesen können, unabhängig davon, welchen Tenant-Kontext
      es gerade für die Schema-/Schul-Auflösung gesetzt hat (ADR-004) – die Instanz ist ja
      definitionsgemäß nicht tenant-gebunden.
    - `edugate_control` (Standard-Datasource, tenant-gebunden) erhält **bewusst keine** Policy für
      `svws_instanz`. Ohne passende Policy liefert RLS mit `FORCE` für diese Rolle grundsätzlich
      keine Zeilen und keine Schreibrechte – der einzige Weg für die Anwendung, `svws_instanz` zu
      lesen oder zu ändern, ist der auditierte Operator-Pfad. Das ist eine zusätzliche,
      DB-erzwungene Absicherung neben dem bereits bestehenden ArchUnit-Test (ADR-009), der
      dieselbe Regel auf Anwendungsebene durchsetzt.
  - Admin-Zugriffe (Anlegen, Bearbeiten, Credentials setzen, Deaktivieren, Liste) laufen
    ausschließlich über `OperatorAccess`, genau wie bei `schultraeger` – `svws_instanz` wird damit
    dem in ADR-009 als "abschließend" bezeichneten Katalog erlaubter Operator-Use-Cases als
    weiterer, hier explizit benannter Fall hinzugefügt (Verwaltung einer weiteren
    mandantenübergreifenden Ressource, analog zur Mandanten-Wurzel).
  - Spätere tenant-spezifische Sichtbarkeit (welcher Schulträger "sieht" welche Instanz in einer
    UI, welches Gateway-Routing für welchen Tenant erlaubt ist) entsteht ausschließlich über die
    Schema-Zuordnung, **nicht** über eine `tenant_id` auf `svws_instanz`.
- Der RLS-Wächter-Test (ADR-008) wird um eine dritte, explizit benannte Regel ergänzt:
  `svws_instanz` hat RLS `ENABLE`+`FORCE`, **keine** `tenant_id`-Spalte, eine
  Operator-Policy und eine Gateway-Read-Policy, aber **keine** Tenant-Policy. Das verhindert, dass
  eine künftige Änderung die Tabelle unbemerkt wieder in das generische Tenant-Muster zurückführt.

## Konsequenzen für bestehende Dokumente

- **ARCHITECTURE.md Kap. 5 (Mandantenmodell, ER-Diagramm):** Die Kante
  `SCHULTRAEGER ||--o{ SVWS_INSTANZ : "betreibt"` sowie das Attribut `schultraeger_id FK` an
  `SVWS_INSTANZ` entfielen; sie unterstellten die hier verworfene 1:n-Bindung. `SVWS_INSTANZ`
  erscheint im Diagramm nur noch über `SVWS_INSTANZ ||--o{ SCHEMA : "hostet"` verbunden.
- **ARCHITECTURE.md Kap. 3 (Kontextabgrenzung):** Die Formulierung „SVWS-Server (Instanzen je
  Schulträger)" im Kontextdiagramm implizierte dieselbe 1:n-Bindung und wird zu einer
  Formulierung ohne Trägerbindung korrigiert.
- **ARCHITECTURE.md Kap. 7 (Verteilungssicht):** Die Beispielknoten „SVWS-Instanzen Schulträger
  A" / „...Schulträger B" implizierten exklusive Instanzen je Schulträger und werden zu
  trägerneutralen Beispielknoten korrigiert (eine SVWS-Zone mit mehreren, ggf. gemeinsam
  genutzten Instanzen).
- **ARCHITECTURE.md Kap. 12 (Glossar):** Der Eintrag „SVWS-Instanz" wird um den Hinweis ergänzt,
  dass eine Instanz mehreren Schulträgern über Schema-Zuordnungen dienen kann.
- **ADR-002:** erhält einen Nachtrag, der `svws_instanz` analog zum bestehenden
  `audit_admin`-Nachtrag in ADR-008 von der generischen Tenant-Tabellen-Regel ausnimmt und auf
  dieses ADR verweist.

## Konsequenzen

### Positiv

- Das Datenmodell entspricht dem tatsächlichen Betriebsmodell (geteilte Instanzen); keine
  fachlich falsche Exklusivität muss später mit einer Breaking-Change-Migration aufgelöst werden.
- RLS bleibt für `svws_instanz` vollständig aktiv (`FORCE`), obwohl die Tabelle kein
  Mandanten-Objekt ist – die Absicherung „nur über `OperatorAccess`" ist damit sowohl auf
  Anwendungsebene (ArchUnit, ADR-009) als auch auf Datenbankebene (RLS ohne
  `edugate_control`-Policy) doppelt verankert.
- Der spätere Schema-/Routing-Auftrag kann `schema.instanz_id` unverändert verwenden; es entsteht
  kein Migrationsdruck auf die dort bereits bestehende Fremdschlüsselbeziehung.

### Negativ / Risiken

- `svws_instanz` folgt damit einem dritten, eigenen RLS-Policy-Muster (neben "Tenant-Tabelle" und
  "Wurzel-Tabelle `schultraeger`") – im RLS-Wächter-Test als eigene, benannte Regel abgebildet und
  damit kontrolliert, aber ein weiterer Sonderfall, den zukünftige Tabellen-Autoren kennen müssen.
- Ohne `tenant_id` auf `svws_instanz` lässt sich "welcher Schulträger nutzt welche Instanz" nicht
  direkt aus dieser Tabelle beantworten, sondern erfordert einen Join über `schema`. Das ist
  beabsichtigt (Konsequenz aus der fachlichen Entscheidung), aber ein Mehraufwand für spätere
  Auswertungen/Reports.

## Verweise

- ADR-002 (Mandantenmodell, präzisiert), ADR-008 (RLS-Wurzeltabelle `schultraeger`, Vorbild für
  das hier gewählte Nicht-Tenant-Policy-Muster), ADR-009 (Operator-Zugriff und Audit,
  Katalog erlaubter Operator-Use-Cases erweitert), ADR-004 (Gateway-Pipeline: Instanz ist nur
  technisches Ziel, keine Autorisierungsebene), ADR-006 (Secret-Handling für
  `credentials_encrypted`), ARCHITECTURE.md Kap. 3/5/7/12
