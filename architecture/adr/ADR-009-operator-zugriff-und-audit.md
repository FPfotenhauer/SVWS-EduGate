# ADR-009: Operator-Zugriff und Audit-Modell für mandantenübergreifende Admin-Operationen

- **Status:** accepted
- **Datum:** 2026-07-17
- **Entscheider:** Franko Pfotenhauer
- **Anlass:** [Issue #3](https://github.com/FPfotenhauer/SVWS-EduGate/issues/3) (openpawde)
- **Ersetzt teilweise:** die `BYPASSRLS`-Festlegung in ADR-002

## Kontext und Problemstellung

ADR-002 sah eine Bypass-Rolle `edugate_operator` mit `BYPASSRLS` vor. Der Dienstleister-Admin arbeitet fachlich mandantenübergreifend (z. B. Auflisten aller Schulträger), die Control Plane soll aber standardmäßig RLS-konform mit Tenant-Kontext arbeiten. Ungeregelt blieb: welche DB-Rolle der Standard ist, wann Operator-Zugriff zulässig ist, wie er auditiert wird und wie Tests verhindern, dass Bypass-Nutzung zur Normalität wird. Ein pauschales `BYPASSRLS` ist zudem ein unsichtbares Rollenattribut, das **alle** Policies gleichzeitig aushebelt.

## Betrachtete Optionen

1. **`BYPASSRLS` als Rollenattribut** (Stand ADR-002): einfach, aber unsichtbar im Schema, wirkt global und macht `FORCE RLS` für diese Rolle bedeutungslos.
2. **Explizite permissive Policies je Tabelle** für `edugate_operator` (`USING (true) WITH CHECK (true)`): funktional gleichwertig, aber sichtbar, pro Tabelle steuerbar, `FORCE RLS` bleibt überall wirksam, Wächter-Test bleibt einheitlich.
3. **Dedizierte Views/`SECURITY DEFINER`-Funktionen** für Cross-Tenant-Zugriffe: maximale Kontrolle, aber deutlich mehr DB-Programmierung und für ein CRUD-Verwaltungstool unverhältnismäßig.

## Entscheidung

Gewählt wurde **Option 2** mit folgendem verbindlichen Zugriffsmodell:

### Rollen und Standard

- Die Control Plane verbindet sich **standardmäßig als `edugate_control`** (unterliegt RLS). Tenant-gebundene Zugriffe setzen pro Transaktion `SET LOCAL edugate.tenant_id = …` über die `TenantContext`-Komponente.
- `edugate_operator` hat **kein** `BYPASSRLS`. Stattdessen definiert die Migration je Tenant-Tabelle (inkl. `schultraeger`, vgl. ADR-008) eine explizite Policy:

  ```sql
  CREATE POLICY <tabelle>_operator_all ON <tabelle>
      FOR ALL TO edugate_operator
      USING (true) WITH CHECK (true);
  ```

- `edugate_gateway_ro` bleibt unverändert tenant-gebunden und read-only (ADR-001/002).

### Erlaubte Operator-Use-Cases (abschließend)

1. Verwaltung der Mandanten-Wurzel: CRUD auf `schultraeger` (per Definition mandantenübergreifend).
2. Mandantenübergreifende Listen, Suchen und Reports des Dienstleister-Admins.
3. Spätere administrative Wartungsjobs (Migrationshelfer, Konsistenzprüfungen) – jeweils einzeln zu benennen.

Alles unterhalb der Mandanten-Wurzel (Schulen, Instanzen, Schemas) wird im Normalfall tenant-gebunden über `edugate_control` bearbeitet, auch vom Dienstleister-Admin: Wer eine Schule bearbeitet, tut das im Kontext genau eines Schulträgers.

### Technische Kapselung

- Es gibt zwei benannte Datasources: `<default>` (User `edugate_control`) und `operator` (User `edugate_operator`).
- Die `operator`-Datasource ist ausschließlich in einer Komponente **`OperatorAccess`** (eigenes Package `…control.operator`) nutzbar. Repositories und Services außerhalb dieses Packages erhalten keine Injektion der Operator-Datasource; ein Architektur-Test (z. B. ArchUnit) erzwingt das.
- Jede Operation über `OperatorAccess` schreibt einen Eintrag in die Tabelle `audit_admin`. **Transaktionsregel:** Bei `SUCCESS` erfolgt der Audit-Eintrag in derselben Transaktion wie die fachliche Änderung (atomar: keine Änderung ohne Audit-Eintrag, kein Audit-Eintrag ohne Änderung). Bei `DENIED` und `ERROR` wird die fachliche Transaktion zurückgerollt und der Audit-Eintrag anschließend in einer **eigenen, unabhängigen Transaktion** geschrieben (`REQUIRES_NEW` bzw. programmatische Transaktion), damit er das Rollback überlebt.

### Audit-Modell (`audit_admin`)

Pflichtfelder je Eintrag:

| Feld | Inhalt |
|------|--------|
| `id`, `occurred_at` | UUID, Zeitstempel (UTC) |
| `admin_subject` | `sub`/`preferred_username` aus dem validierten Token |
| `action` | z. B. `SCHULTRAEGER_CREATE`, `SCHULTRAEGER_LIST`, `SCHULTRAEGER_DEACTIVATE` |
| `entity_type`, `entity_id` | betroffene Entität (`entity_id` nullable bei Listen) |
| `tenant_id` | betroffener Mandant (nullable bei echten Cross-Tenant-Listen) |
| `outcome` | `SUCCESS` / `DENIED` / `ERROR` |
| `details` | optionales JSONB (z. B. Filter, Begründung) – niemals Secrets oder Schülerdaten |

`audit_admin` ist append-only: `edugate_control` und `edugate_operator` erhalten nur `INSERT` (und `SELECT` für die spätere Audit-Ansicht), kein `UPDATE`/`DELETE`. Dieses Admin-Audit ist vom Gateway-Zugriffs-Audit (ADR-004) getrennt zu halten.

### Test-Invarianten

1. Cross-Tenant-Negativtest: Als `edugate_control` mit Tenant-Kontext A liefert ein `SELECT` auf Daten von Tenant B null Zeilen.
2. Jede `OperatorAccess`-Operation erzeugt genau einen `audit_admin`-Eintrag. Im Happy Path atomar mit der Änderung; im `ERROR`-Fall (Test provoziert einen Fehler mit Rollback der Fachtransaktion) ist der Audit-Eintrag mit `outcome = ERROR` dennoch persistiert, die fachliche Änderung dagegen nicht.
3. Architektur-Test: Die Operator-Datasource wird außerhalb des `operator`-Packages nicht referenziert.
4. `audit_admin` verweigert `UPDATE`/`DELETE` für Anwendungsrollen.

## Konsequenzen

### Positiv

- Der wichtigste Schutzmechanismus (RLS mit FORCE) bleibt für **alle** Rollen aktiv; Ausnahmen stehen sichtbar und pro Tabelle im Schema.
- Klare, testbare Regel „Normalzugriff vs. Operator-Zugriff“; der vertikale Durchstich (Schulträger-CRUD) ist eindeutig dem Operator-Pfad inkl. Audit zugeordnet.
- Bypass-Nutzung kann nicht unbemerkt zur Normalität werden (ArchUnit + Audit-Pflicht).

### Negativ / Risiken

- Zweite Datasource und Audit-Schreibpfad erhöhen den Scaffolding-Umfang moderat.
- `SCHULTRAEGER_LIST`-Audits können bei intensiver UI-Nutzung Volumen erzeugen → bewusst in Kauf genommen; Aufbewahrung/Verdichtung wird bei Bedarf in einem eigenen ADR geregelt.
- Der Fehler-Audit-Pfad in eigener Transaktion kann im Extremfall (z. B. Datenbank nicht erreichbar) selbst fehlschlagen; als Rückfallebene wird der Vorgang zusätzlich im strukturierten Anwendungslog protokolliert.

## Nachtrag (SVWS-Serververwaltung): `svws_instanz` als weiterer Operator-Use-Case

Der Katalog erlaubter Operator-Use-Cases wird um einen vierten, hier explizit benannten Fall
ergänzt: **Verwaltung von `svws_instanz`** (Anlegen, Bearbeiten, Credentials setzen,
Deaktivieren, Liste). `svws_instanz` ist gemäß [ADR-011](./ADR-011-svws-instanz-als-geteilte-betriebsressource.md)
eine mandantenübergreifende Betriebsressource, keine tenant-gebundene Fachtabelle – ihre
CRUD-Verwaltung läuft daher, analog zur Mandanten-Wurzel `schultraeger`, vollständig über
`OperatorAccess` statt über den tenant-gebundenen `edugate_control`-Standardpfad. Anders als bei
`schultraeger` (Wurzel-Policy auf die eigene `id`, ADR-008) erhält `edugate_control` für
`svws_instanz` gar keine Policy: Der Operator-Pfad ist damit sowohl anwendungsseitig
(ArchUnit-Test in diesem ADR) als auch datenbankseitig (RLS ohne passende Policy für
`edugate_control`) der einzig mögliche Zugriffsweg.

## Verweise

- Issue #3, ADR-002 (präzisiert/teilweise ersetzt), ADR-008 (Policy der Wurzeltabelle), ADR-004 (getrenntes Gateway-Audit), ADR-005 (Token-Identität), ADR-011 (svws_instanz als geteilte Betriebsressource)
