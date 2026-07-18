# SVWS-EduGate

Werkzeug für Dienstleister, die SVWS-Server für mehrere Schulträger in einem BSI-zertifizierten
Rechenzentrum betreiben. SVWS-EduGate besteht aus einer **Control Plane** (Serververwaltung) und
einem **Gateway** (mandantengetrennter API-Zugriff auf Schuldaten).

Dieser Stand implementiert den ersten vertikalen Durchstich: Ein Dienstleister-Admin meldet sich
über Keycloak an und kann Schulträger anlegen, auflisten, anzeigen, bearbeiten und deaktivieren –
von der Vue-Oberfläche über die Control-Plane-API bis PostgreSQL, mit aktiver Row-Level Security
und auditiertem Admin-Zugriff.

Verbindliche Architekturdokumentation: [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md)
und die [ADRs](architecture/adr/README.md). Nutzerorientierte Dokumentation (Betreiber,
Administration, Anwender, Sicherheit/Compliance, Entwicklung): [`docs/README.md`](docs/README.md).
Sicherheits- und Compliance-Nachweise: [`compliance/README.md`](compliance/README.md).

## Quickstart

```bash
cp .env.example .env
# .env öffnen und alle change-me-Platzhalter setzen, insbesondere:
#   EDUGATE_MASTER_KEY -> openssl rand -base64 32   (ADR-006)
docker compose up --build -d
```

Nach dem Start (Healthchecks aller Services müssen grün sein, `docker compose ps` prüfen):

| Dienst | URL | Hinweis |
|---|---|---|
| Admin-SPA (Frontend) | http://localhost:5173 | Login-Redirect zu Keycloak |
| Control Plane API | http://localhost:8080/admin/api/v1 | erfordert Bearer-Token |
| Control Plane OpenAPI | http://localhost:8080/admin/api/v1/openapi | |
| Control Plane Swagger UI | http://localhost:8080/admin/api/v1/swagger-ui | |
| Control Plane Health | http://localhost:8080/q/health | |
| Gateway `/ping` | http://localhost:8081/gateway/api/v1/ping | erfordert Bearer-Token |
| Gateway Health | http://localhost:8081/q/health | |
| Keycloak | http://localhost:8180 | Realm `edugate` (automatisch importiert) |

### Dev-Login

Beim Öffnen von http://localhost:5173 leitet die SPA automatisch zu Keycloak weiter. Dev-Benutzer:

- **Benutzername:** `admin@edugate.local`
- **Passwort:** der Wert von `EDUGATE_DEV_ADMIN_PASSWORD` aus `.env`
- **Rolle:** `dienstleister-admin` (Vollzugriff, auditiert)

Der Access-Token wird ausschließlich im Speicher der SPA gehalten (nie in `localStorage`) und
geht mit einem Seiten-Reload verloren – erneutes Anmelden ist dann nötig.

### Gateway `/ping` mit Client-Credentials testen

```bash
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/edugate/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=edugate-demo-client" \
  -d "client_secret=$EDUGATE_DEMO_CLIENT_SECRET" \
  | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/gateway/api/v1/ping
# {"status":"ok"}

curl http://localhost:8081/gateway/api/v1/ping
# 401 ohne Token
```

### Control-Plane-API ohne/mit Token prüfen

```bash
curl -i http://localhost:8080/admin/api/v1/schultraeger
# 401 Problem+JSON ohne Token

# Token über die SPA-Anmeldung beziehen (Browser-DevTools, Network-Tab) oder analog zum
# Gateway-Beispiel oben einen Nutzer-Token per Password-Grant beziehen (nur für lokale Tests;
# der Password-Grant ist für edugate-frontend nicht aktiviert, da Authorization-Code+PKCE
# genutzt wird - die Prüfung erfolgt daher am einfachsten über die SPA selbst).
```

## Entwicklung

### Backend (Java 21, Quarkus, Maven-Multi-Module)

```bash
cd backend
./mvnw quarkus:dev -pl edugate-control-plane -am   # Control Plane im Dev-Modus (Port 8080)
./mvnw quarkus:dev -pl edugate-gateway -am         # Gateway im Dev-Modus (Port 8081)
```

Für `quarkus:dev` außerhalb von Docker müssen PostgreSQL und Keycloak separat laufen (z. B. via
`docker compose up postgres keycloak -d`) und die Umgebungsvariablen aus `.env` gesetzt sein.

Module:

- `edugate-core` – Domänenmodell, `SecretStore`-Port (AES-256-GCM, ADR-006), keine
  Quarkus-Laufzeitabhängigkeiten.
- `edugate-control-plane` – REST-API `/admin/api/v1`, Mandantenmodell mit PostgreSQL-RLS
  (ADR-002/ADR-008), auditierter Operator-Zugriffspfad (ADR-009).
- `edugate-gateway` – Data-Plane-Service (in diesem Stand nur Skeleton: OIDC, Health, `/ping`;
  Proxy-/Mandanten-Auflösungslogik folgt gemäß ADR-004 in einem späteren Auftrag).

### Frontend (Vue 3, TypeScript, Vite, Pinia)

```bash
cd frontend
npm install
cp .env.example .env   # nur nötig für Entwicklung außerhalb von Docker Compose
npm run dev
```

### Tests

```bash
cd backend && ./mvnw verify   # Unit-/Integrationstests (Testcontainers, benötigt Docker)
cd frontend && npm run test   # Vitest
cd frontend && npm run lint   # ESLint + Prettier
```

`./mvnw verify` deckt u. a. ab:

- SecretStore-Roundtrip- und Manipulationserkennungstests (`edugate-core`)
- RLS-Wächter-Test gemäß ADR-008 (beide Prüfregeln: Fachtabellen mit `tenant_id` sowie die
  Wurzeltabelle `schultraeger`)
- Cross-Tenant-Negativtest (`schule` und `schultraeger`)
- Schulträger-CRUD-Happy-Path, Validierungsfehler (400), 401/403-Fälle
- Audit-Invarianten (genau ein `audit_admin`-Eintrag je Operation, ERROR-Audit übersteht
  Rollback, kein `UPDATE`/`DELETE` auf `audit_admin` für Anwendungsrollen)
- ArchUnit-Test: die `operator`-Datasource wird außerhalb von
  `de.svws_nrw.edugate.control.operator` nicht referenziert

## Architektur

Verbindlich: [`architecture/ARCHITECTURE.md`](architecture/ARCHITECTURE.md) (arc42) und die
Architekturentscheidungen unter [`architecture/adr/`](architecture/adr/README.md), insbesondere:

- [ADR-002](architecture/adr/ADR-002-mandantenmodell-postgresql-rls.md) – Mandantenmodell mit
  PostgreSQL Row-Level Security
- [ADR-008](architecture/adr/ADR-008-rls-wurzeltabelle-schultraeger.md) – RLS-Behandlung der
  Wurzeltabelle `schultraeger`
- [ADR-009](architecture/adr/ADR-009-operator-zugriff-und-audit.md) – Operator-Zugriff und
  Admin-Audit

## Lizenz

BSD-3-Clause, siehe [`LICENSE`](LICENSE).
