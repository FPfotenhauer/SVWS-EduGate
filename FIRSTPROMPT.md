# SVWS-EduGate – Erster Prompt: Projekt-Scaffolding

> Diese Datei ist der initiale Arbeitsauftrag an einen Coding-Agenten (GitHub Copilot, Claude Code o. ä.).
> Sie wird im Repo versioniert, damit nachvollziehbar bleibt, mit welcher Aufgabenstellung das Projekt gestartet wurde.

---

## Rolle und verbindliche Grundlagen

Du arbeitest im Repository **SVWS-EduGate**. Lies zuerst vollständig:

1. `architecture/ARCHITECTURE.md` (arc42, Kapitel 1–12)
2. Alle ADRs unter `architecture/adr/` (ADR-001 bis ADR-009; beachte insbesondere ADR-008 und ADR-009, die ADR-002 präzisieren)

Diese Dokumente sind **verbindlich**. Wenn du bei der Umsetzung auf einen Widerspruch oder eine Lücke stößt, triff keine stillschweigende Abweichung: Stelle die Frage bzw. schlage die Abweichung explizit vor und begründe sie. Architekturrelevante Abweichungen erfordern ein neues ADR (Vorlage: `architecture/adr/ADR-TEMPLATE.md`).

## Ziel dieses Auftrags

Erstelle das **lauffähige Projektskelett** mit genau **einem vertikalen Durchstich**: Der Dienstleister-Admin kann sich über Keycloak anmelden und **Schulträger anlegen, auflisten, bearbeiten und deaktivieren** – durchgängig von der Vue-Oberfläche über die Control-Plane-API bis in die PostgreSQL-Datenbank mit aktiver Row-Level Security.

Nicht mehr. Der Durchstich beweist die Architektur; Features folgen in späteren Aufträgen.

## Repository-Struktur (exakt so anlegen)

```
SVWS-EduGate/
├── architecture/            # existiert bereits – nicht verändern
├── backend/
│   ├── pom.xml              # Maven-Parent (Multi-Module)
│   ├── mvnw / mvnw.cmd / .mvn/
│   ├── edugate-core/        # Domänenmodell, gemeinsame Typen, SecretStore-Port
│   ├── edugate-control-plane/  # Quarkus-Service, REST /admin/api/v1
│   └── edugate-gateway/     # Quarkus-Service, in diesem Auftrag nur Skelett
├── frontend/                # Vue 3 + TypeScript + Vite + Pinia
├── docker/
│   ├── keycloak/realm-edugate.json   # Realm-Import (s. u.)
│   └── postgres/init/                # DB-Rollen-Init (s. u.)
├── docker-compose.yml
├── .env.example
├── .gitignore
├── LICENSE                  # BSD-3-Clause
└── README.md
```

## Technologie-Vorgaben

| Bereich | Vorgabe |
|---------|---------|
| Java | 21 (LTS) |
| Backend | Quarkus, aktuelle 3.x-Version; Maven-Multi-Module mit Wrapper |
| Quarkus-Extensions (Control Plane) | `rest-jackson`, `hibernate-orm-panache`, `jdbc-postgresql`, `flyway`, `oidc`, `smallrye-health`, `smallrye-openapi`, `hibernate-validator` |
| Quarkus-Extensions (Gateway) | `rest-jackson`, `oidc`, `smallrye-health` (mehr nicht in diesem Auftrag) |
| Frontend | Vue 3 (Composition API, `<script setup lang="ts">`), TypeScript strict, Vite, Pinia, Vue Router (History-Mode) |
| Datenbank | PostgreSQL 16, Migrationen ausschließlich über Flyway |
| IAM | Keycloak (aktuelle Version), Start im Dev-Modus mit Realm-Import |
| Tests | JUnit 5 + RestAssured + Testcontainers (Backend), Vitest (Frontend) |
| API-Fehlerformat | RFC 7807 `application/problem+json` |
| Node | 22 LTS |

## Detailanforderungen

### 1. `edugate-core`

- Reines Java-Modul (keine Quarkus-Laufzeitabhängigkeiten außer Jakarta-Annotationen, falls nötig).
- Domänentypen gemäß ER-Modell in `ARCHITECTURE.md` Kap. 5: `Schultraeger`, `Schule`, `SvwsInstanz`, `Schema`, Enum `Umgebung { PRODUKTIV, TEST }`, Statusmodell `InstanzStatus { OK, DEGRADED, UNREACHABLE }`.
- Interface `SecretStore` (Port gemäß ADR-006) mit Methoden `encrypt`, `decrypt`, `keyVersion()` – in diesem Auftrag nur das Interface plus eine AES-256-GCM-Implementierung `AesGcmSecretStore` (Master-Key aus Umgebungsvariable `EDUGATE_MASTER_KEY`, zufälliger Nonce je Datensatz, `key_version` im Chiffrat kodiert). Unit-Tests für Roundtrip und Manipulationserkennung (GCM-Tag).

### 2. Flyway-Migration `V1__initial_schema.sql` (Control Plane)

Gemäß ADR-002 in der durch ADR-008 und ADR-009 präzisierten Fassung, wörtlich umzusetzen:

- Tabellen `schultraeger`, `schule`, `svws_instanz`, `schema` entsprechend dem ER-Modell; alle IDs `uuid` mit `gen_random_uuid()`; Zeitstempel `created_at`/`updated_at`; `schultraeger` zusätzlich mit `aktiv boolean not null default true` (Deaktivieren statt Löschen).
- `tenant_id uuid not null` auf `schule`, `svws_instanz`, `schema` mit FK auf `schultraeger(id)`; Indizes beginnend mit `(tenant_id, …)`. **`schultraeger` selbst erhält keine `tenant_id`** (ADR-008).
- **Row-Level Security** auf allen vier Tabellen aktivieren (`ENABLE` + `FORCE`):
  - `schule`, `svws_instanz`, `schema`: Tenant-Policy `tenant_id = current_setting('edugate.tenant_id')::uuid`.
  - `schultraeger`: Wurzel-Policy `id = current_setting('edugate.tenant_id')::uuid` (ADR-008).
  - Zusätzlich je Tabelle die explizite Operator-Policy `FOR ALL TO edugate_operator USING (true) WITH CHECK (true)` (ADR-009).
- Tabelle **`audit_admin`** gemäß ADR-009 (Felder: `id`, `occurred_at`, `admin_subject`, `action`, `entity_type`, `entity_id` nullable, `tenant_id` nullable, `outcome`, `details jsonb` nullable). Append-only: Anwendungsrollen erhalten nur `INSERT` und `SELECT`, kein `UPDATE`/`DELETE`.
- Datenbankrollen (Anlage der Rollen in `docker/postgres/init/01-roles.sql`, Grants in der Migration):
  - `edugate_control` – CRUD auf den Fachtabellen, unterliegt RLS; `INSERT`/`SELECT` auf `audit_admin`.
  - `edugate_gateway_ro` – nur `SELECT` auf `schultraeger`, `schule`, `svws_instanz`, `schema`, unterliegt RLS.
  - `edugate_operator` – CRUD auf den Fachtabellen über die expliziten Operator-Policies; `INSERT`/`SELECT` auf `audit_admin`. **Kein `BYPASSRLS`** (ADR-009).
- **RLS-Wächter-Test** (Testcontainers) mit den zwei Prüfregeln aus ADR-008: (1) Jede Tabelle mit Spalte `tenant_id` hat RLS `ENABLE`+`FORCE` und eine Policy auf `tenant_id` gegen `current_setting('edugate.tenant_id')`; (2) `schultraeger` hat RLS `ENABLE`+`FORCE` und eine Policy auf `id` gegen `current_setting('edugate.tenant_id')`. Dieser Test ist Pflichtbestandteil der CI-Denkweise des Projekts (ARCHITECTURE.md Kap. 11).

### 3. `edugate-control-plane`

- REST-Basis `/admin/api/v1`, OpenAPI unter `/admin/api/v1/openapi` aktiv.
- Schichtung gemäß ADR-003: `resource` (REST/DTOs) → `service` (Anwendungslogik) → `domain` (aus `edugate-core`) → `repository` (Panache). Keine Entities in API-Responses; DTOs als Java-Records.
- Endpunkte in diesem Auftrag:
  - `GET /admin/api/v1/schultraeger` (Pagination: `page`, `size`, Default 25, max 100; Filter `q` auf Name/Trägernummer)
  - `POST /admin/api/v1/schultraeger`
  - `GET /admin/api/v1/schultraeger/{id}`
  - `PUT /admin/api/v1/schultraeger/{id}`
  - `DELETE /admin/api/v1/schultraeger/{id}` → setzt `aktiv = false` (kein physisches Löschen)
- Alle Endpunkte erfordern die Realm-Rolle `dienstleister-admin` (`@RolesAllowed`). Requests ohne gültiges Token → 401, ohne Rolle → 403, jeweils als Problem-JSON.
- Datenbankzugriff gemäß ADR-009, zwei benannte Datasources:
  - `<default>` als `edugate_control` (RLS-gebunden). Tenant-gebundene Zugriffe setzen `SET LOCAL edugate.tenant_id = …` pro Transaktion über eine wiederverwendbare `TenantContext`-Komponente (wird vom Gateway später mitgenutzt; in diesem Auftrag nur durch Tests exerziert, s. u.).
  - `operator` als `edugate_operator`, ausschließlich nutzbar über die Komponente **`OperatorAccess`** im Package `…control.operator`. Ein ArchUnit-Test erzwingt, dass die Operator-Datasource außerhalb dieses Packages nicht referenziert wird.
- **Alle fünf Schulträger-Endpunkte laufen über `OperatorAccess`** (Schulträger-CRUD ist per Definition mandantenübergreifend, ADR-009) und schreiben einen `audit_admin`-Eintrag mit den Actions `SCHULTRAEGER_LIST`, `SCHULTRAEGER_CREATE`, `SCHULTRAEGER_READ`, `SCHULTRAEGER_UPDATE`, `SCHULTRAEGER_DEACTIVATE` (`admin_subject` aus dem Token). **Transaktionsregel gemäß ADR-009:** `SUCCESS`-Audit atomar in derselben Transaktion wie die fachliche Änderung; `DENIED`/`ERROR`-Audit nach Rollback der Fachtransaktion in einer eigenen, unabhängigen Transaktion (`REQUIRES_NEW`), zusätzlich Eintrag im strukturierten Anwendungslog.
- Health: `/q/health/live` und `/q/health/ready` (Readiness prüft DB).
- Integrationstests (Testcontainers + RestAssured):
  - CRUD-Happy-Path, Validierungsfehler (leerer Name → 400), 401/403-Fälle mit `@TestSecurity`.
  - Audit-Invariante: Jede Operation über `OperatorAccess` erzeugt genau einen `audit_admin`-Eintrag; im provozierten `ERROR`-Fall ist der Eintrag (`outcome = ERROR`) trotz Rollback der Fachtransaktion persistiert, die fachliche Änderung nicht; `UPDATE`/`DELETE` auf `audit_admin` wird für Anwendungsrollen verweigert.
  - Cross-Tenant-Negativtest über `TenantContext`: Als `edugate_control` mit Tenant-Kontext A liefert ein `SELECT` auf Zeilen von Tenant B (Testdaten direkt eingefügt) null Zeilen – für `schule` **und** für `schultraeger` (Wurzel-Policy, ADR-008).

### 4. `edugate-gateway` (nur Skelett)

- Eigener Quarkus-Service, Basis `/gateway/api/v1`.
- In diesem Auftrag ausschließlich: OIDC-Konfiguration (Token-Validierung gegen Keycloak), `/q/health/*`, und ein Endpunkt `GET /gateway/api/v1/ping`, der ein gültiges Token verlangt und `{ "status": "ok" }` liefert.
- Datasource als `edugate_gateway_ro` konfigurieren, aber **keine** Proxy-, Auflösungs- oder Audit-Logik implementieren (ADR-004 kommt in einem späteren Auftrag).

### 5. Keycloak-Realm-Import (`docker/keycloak/realm-edugate.json`)

Gemäß ADR-005:

- Realm `edugate`.
- Realm-Rollen `dienstleister-admin`, `schultraeger-admin`.
- Public Client `edugate-frontend` (Authorization Code + PKCE, Redirect `http://localhost:5173/*`).
- Confidential Client `edugate-demo-client` (Client Credentials) mit Beispiel-Scope `schule:123456:read` – nur damit der Gateway-`/ping` testbar ist.
- Dev-Benutzer `admin@edugate.local` mit Rolle `dienstleister-admin` (Passwort über `.env`, nicht im Realm-JSON hartkodieren; nutze Keycloaks Platzhalter- bzw. Bootstrap-Mechanismus oder dokumentiere das manuelle Anlegen im README).

### 6. `docker-compose.yml`

Netzzonen gemäß ADR-007 als getrennte Compose-Netze nachbilden:

| Netz | Services |
|------|----------|
| `zone-mgmt` | `postgres`, `keycloak`, `control-plane`, `frontend` (Dev) |
| `zone-gw` | `gateway` |

- `gateway` hängt zusätzlich an `zone-mgmt` **nur**, um Keycloak (JWKS) und PostgreSQL (read-only) zu erreichen – kommentiere im Compose-File, dass dies in Produktion durch Firewall-Regeln auf genau diese Ziele beschränkt wird.
- `zone-svws` als Netz bereits anlegen (leer, mit Kommentar), damit spätere SVWS-Testinstanzen ohne Umbau andocken.
- Ports nach außen: nur Frontend-Dev (5173), Control Plane (8080), Gateway (8081), Keycloak (8180). PostgreSQL ohne Host-Port-Mapping.
- Healthchecks für alle Services; `control-plane` startet erst nach `postgres` und `keycloak` (condition: service_healthy).
- Alle Secrets/Passwörter ausschließlich über `.env`; `.env.example` mit Platzhaltern und erklärenden Kommentaren (inkl. `EDUGATE_MASTER_KEY` mit Hinweis auf ADR-006 und Generierungskommando `openssl rand -base64 32`).

### 7. `frontend/`

- Vite-Projekt mit Vue 3, TypeScript strict, Pinia, Vue Router; ESLint + Prettier.
- OIDC-Login gegen Keycloak (Authorization Code + PKCE, z. B. via `keycloak-js` oder `oidc-client-ts` – Entscheidung kurz im Code-Kommentar begründen); Access-Token nur im Speicher halten, **niemals** in `localStorage` (Sicherheitslinie aus SVWS-Conference fortführen).
- Views in diesem Auftrag: Login-Redirect, Schulträger-Liste (Tabelle mit Pagination und Suche), Schulträger-Formular (Anlegen/Bearbeiten), Deaktivieren mit Bestätigungsdialog.
- Ein Pinia-Store `schultraegerStore` mit typisiertem API-Client (generiert aus OpenAPI oder handgeschrieben mit gemeinsamen `ProblemDetail`-Typ für Fehler).
- Deutsche UI-Texte; barrierearme Grundlagen (Labels, Fokusreihenfolge, Tastaturbedienung der Tabelle).

### 8. README-Quickstart aktualisieren

- Abschnitt „Quickstart“: `cp .env.example .env` → Werte setzen → `docker compose up --build -d` → URLs und Dev-Login.
- Abschnitt „Entwicklung“: Backend (`./mvnw quarkus:dev` je Modul), Frontend (`npm run dev`), Tests (`./mvnw verify`, `npm run test`).
- Verweis auf `architecture/ARCHITECTURE.md` und die ADRs.

## Nicht-Ziele dieses Auftrags (bewusst weglassen)

- Keine Gateway-Proxy-Logik, keine Mandanten-Auflösung, kein Gateway-Zugriffs-Audit (ADR-004 → späterer Auftrag). Das **Admin-Audit `audit_admin` nach ADR-009 ist davon getrennt und Teil dieses Auftrags** (s. o.).
- Kein SVWS-Sync, keine SVWS-Client-Schicht, keine Verwaltung von Schulen/Instanzen/Schemas über die API (nur die Tabellen existieren bereits).
- Keine `schultraeger-admin`-Funktionalität, kein Self-Service.
- Keine externe Zone, kein Reverse Proxy, kein mTLS (Dev-Umgebung; ADR-007 dokumentiert das Zielbild).
- Kein CI-Workflow in diesem Auftrag (folgt separat) – aber alle Tests müssen lokal grün laufen.

## Abnahmekriterien (alle müssen erfüllt sein)

1. `docker compose up --build -d` startet auf einem frischen Checkout (nach `.env`-Setup) fehlerfrei; alle Healthchecks werden grün.
2. Login unter `http://localhost:5173` über Keycloak mit dem Dev-Admin funktioniert; ohne Login sind die Views nicht erreichbar.
3. Schulträger lassen sich über die UI anlegen, suchen, bearbeiten und deaktivieren; Deaktivierte sind als solche gekennzeichnet.
4. `curl` ohne Token auf `GET /admin/api/v1/schultraeger` → 401 Problem-JSON; mit Token ohne Rolle → 403.
5. `GET /gateway/api/v1/ping` liefert mit Client-Credentials-Token des Demo-Clients 200, ohne Token 401.
6. `./mvnw verify` ist grün, inklusive RLS-Wächter-Test (beide Regeln aus ADR-008), Cross-Tenant-Negativtest, Audit-Invarianten-Tests, ArchUnit-Test zur Operator-Datasource und SecretStore-Tests; `npm run test` und `npm run lint` sind grün.
6a. Nach Anlegen und Deaktivieren eines Schulträgers über die UI sind die zugehörigen `audit_admin`-Einträge (CREATE, DEACTIVATE mit `admin_subject` des Dev-Admins) in der Datenbank nachweisbar.
7. Keine Secrets im Repo (`git grep` auf offensichtliche Muster ist sauber); `.env.example` enthält nur Platzhalter.
8. OpenAPI-Dokument der Control Plane ist abrufbar und beschreibt alle fünf Endpunkte.

## Arbeitsweise

- Arbeite in kleinen, thematisch geschlossenen Commits (Conventional Commits: `feat:`, `chore:`, `test:`, `docs:` …).
- Bei jedem Schritt, der von den ADRs abweicht oder sie interpretiert: erst fragen bzw. Vorschlag mit Begründung, dann umsetzen.
- Halte generierten Boilerplate minimal; lösche ungenutzte Quarkus-/Vite-Beispieldateien.
- Aktualisiere `README.md` und – falls sich aus der Umsetzung Präzisierungen ergeben – die betroffenen Stellen in `architecture/` in einem separaten `docs:`-Commit.
