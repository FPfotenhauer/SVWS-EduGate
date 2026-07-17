# SVWS-EduGate – Erster Prompt: Projekt-Scaffolding

> Diese Datei ist der initiale Arbeitsauftrag an einen Coding-Agenten (GitHub Copilot, Claude Code o. ä.).
> Sie wird im Repo versioniert, damit nachvollziehbar bleibt, mit welcher Aufgabenstellung das Projekt gestartet wurde.

---

## Rolle und verbindliche Grundlagen

Du arbeitest im Repository **SVWS-EduGate**. Lies zuerst vollständig:

1. `architecture/ARCHITECTURE.md` (arc42, Kapitel 1–12)
2. Alle ADRs unter `architecture/adr/` (ADR-001 bis ADR-007)

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

Gemäß ADR-002, wörtlich umzusetzen:

- Tabellen `schultraeger`, `schule`, `svws_instanz`, `schema` entsprechend dem ER-Modell; alle IDs `uuid` mit `gen_random_uuid()`; Zeitstempel `created_at`/`updated_at`; `schultraeger` zusätzlich mit `aktiv boolean not null default true` (Deaktivieren statt Löschen).
- `tenant_id uuid not null` auf `schule`, `svws_instanz`, `schema` mit FK auf `schultraeger(id)`; Indizes beginnend mit `(tenant_id, …)`.
- **Row-Level Security** auf allen Tenant-Tabellen aktivieren (`ENABLE` + `FORCE`). Policy: `tenant_id = current_setting('edugate.tenant_id')::uuid`.
- Datenbankrollen (Anlage der Rollen in `docker/postgres/init/01-roles.sql`, Grants in der Migration):
  - `edugate_control` – CRUD auf allen Tabellen, unterliegt RLS.
  - `edugate_gateway_ro` – nur `SELECT` auf `schultraeger`, `schule`, `svws_instanz`, `schema`, unterliegt RLS.
  - `edugate_operator` – `BYPASSRLS` für den Dienstleister-Kontext (Nutzung wird später auditiert).
- **RLS-Wächter-Test** (Testcontainers): schlägt fehl, sobald irgendeine Tabelle mit Spalte `tenant_id` kein aktives RLS inkl. FORCE hat. Dieser Test ist Pflichtbestandteil der CI-Denkweise des Projekts (ARCHITECTURE.md Kap. 11).

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
- Tenant-Kontext: Da der Dienstleister-Admin mandantenübergreifend arbeitet, nutzt die Control Plane in diesem Auftrag die Verbindung als `edugate_control` und setzt bei tenant-gebundenen Zugriffen `SET LOCAL edugate.tenant_id = …` pro Transaktion. Kapsle das in einer wiederverwendbaren `TenantContext`-Komponente – sie wird vom Gateway später mitgenutzt.
- Health: `/q/health/live` und `/q/health/ready` (Readiness prüft DB).
- Integrationstests (Testcontainers + RestAssured): CRUD-Happy-Path, Validierungsfehler (leerer Name → 400), 401/403-Fälle mit `@TestSecurity`.

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

- Keine Gateway-Proxy-Logik, keine Mandanten-Auflösung, kein Audit-Log (ADR-004 → späterer Auftrag).
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
6. `./mvnw verify` ist grün, inklusive RLS-Wächter-Test und SecretStore-Tests; `npm run test` und `npm run lint` sind grün.
7. Keine Secrets im Repo (`git grep` auf offensichtliche Muster ist sauber); `.env.example` enthält nur Platzhalter.
8. OpenAPI-Dokument der Control Plane ist abrufbar und beschreibt alle fünf Endpunkte.

## Arbeitsweise

- Arbeite in kleinen, thematisch geschlossenen Commits (Conventional Commits: `feat:`, `chore:`, `test:`, `docs:` …).
- Bei jedem Schritt, der von den ADRs abweicht oder sie interpretiert: erst fragen bzw. Vorschlag mit Begründung, dann umsetzen.
- Halte generierten Boilerplate minimal; lösche ungenutzte Quarkus-/Vite-Beispieldateien.
- Aktualisiere `README.md` und – falls sich aus der Umsetzung Präzisierungen ergeben – die betroffenen Stellen in `architecture/` in einem separaten `docs:`-Commit.
