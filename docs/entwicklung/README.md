# Entwicklung

Zielgruppe: Entwicklerinnen, Entwickler und Coding-Agenten, die an SVWS-EduGate arbeiten.

## Wo Architektur und ADRs zu lesen sind

- Gesamtarchitektur (arc42): [`architecture/ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md)
- Einzelne Architekturentscheidungen (ADRs): [`architecture/adr/`](../../architecture/adr/README.md)
- Compliance-Hintergrund, falls sicherheitsrelevant: [`compliance/README.md`](../../compliance/README.md)

Vor einer nicht trivialen Änderung lohnt sich ein Blick in Kapitel 4
(„Lösungsstrategie“, ADR-Übersicht je Problem) und Kapitel 12 („Glossar“) der
`ARCHITECTURE.md`.

## Die API des SVWS-Servers

Wer sich mit der API des SVWS-Servers (des externen Systems, das EduGate verwaltet) befassen
muss – Server-API, Privileged-/Root-API oder die künftige External-API – findet Struktur,
Auth-Modell und Hintergrundwissen dazu in
[`svws-server-api.md`](./svws-server-api.md). Die zugehörigen OpenAPI-Beschreibungen liegen
unter [`examples/`](../../examples/).

## Regel: architekturrelevante Änderungen brauchen ein ADR

Einmal getroffene Architekturentscheidungen werden nicht nachträglich verändert. Eine
architekturrelevante Änderung (z. B. neue Technologie, geänderter Mandanten- oder
Sicherheitsmechanismus, geänderte Netzzonen) bekommt entweder:

- ein neues ADR nach [`architecture/adr/ADR-TEMPLATE.md`](../../architecture/adr/ADR-TEMPLATE.md)
  (fortlaufend nummeriert, in [`architecture/adr/README.md`](../../architecture/adr/README.md)
  verlinkt), oder
- eine explizite, im Pull Request nachvollziehbare Begründung, warum kein neues ADR nötig ist.

Eine revidierte Entscheidung erhält immer ein neues ADR, das die alte auf `superseded` setzt –
bestehende ADRs werden nicht rückwirkend umgeschrieben.

## Test- und Nachweisgedanke

Sicherheitsrelevante Eigenschaften werden über automatisierte Tests abgesichert, nicht nur über
Code-Review. Wesentliche Bausteine (siehe [Repository-Root-`README.md`](../../README.md),
Abschnitt „Tests“):

- **RLS-Wächter-Test** – prüft, dass Row-Level Security auf allen Mandanten-Tabellen sowie der
  Wurzeltabelle `schultraeger` aktiv und erzwungen ist ([ADR-008](../../architecture/adr/ADR-008-rls-wurzeltabelle-schultraeger.md)).
- **Cross-Tenant-Negativtest** – stellt sicher, dass ein Mandant keine Daten eines anderen sieht.
- **Audit-Invarianten** – genau ein `audit_admin`-Eintrag je Operation, Fehler-Audit übersteht
  Rollback, kein `UPDATE`/`DELETE` auf `audit_admin` für Anwendungsrollen
  ([ADR-009](../../architecture/adr/ADR-009-operator-zugriff-und-audit.md)).
- **ArchUnit-Test** – die `operator`-Datasource wird ausschließlich innerhalb des
  `OperatorAccess`-Packages referenziert.
- **SecretStore-Tests** – Verschlüsselungs-Roundtrip und Erkennung manipulierter Chiffrate
  ([ADR-006](../../architecture/adr/ADR-006-secret-handling-svws-credentials.md)).

Neue sicherheitsrelevante Funktionalität sollte, wo möglich, denselben Gedanken folgen: ein Test
ist ein Nachweis, nicht nur eine Qualitätssicherungsmaßnahme (vgl.
[`compliance/nachweis-checkliste.md`](../../compliance/nachweis-checkliste.md)).

## Orientierung zur Repo-Struktur

```text
backend/                  Maven-Multi-Module (Java 21, Quarkus)
  edugate-core/            Domänenmodell, SecretStore-Port, keine Quarkus-Laufzeitabhängigkeiten
  edugate-control-plane/   REST-API /admin/api/v1, Mandantenmodell mit PostgreSQL-RLS
  edugate-gateway/         Data-Plane-Service (Gateway)
frontend/                 Vue 3, TypeScript, Vite, Pinia (Admin-SPA)
architecture/              ARCHITECTURE.md und ADRs
compliance/                Sicherheits- und Compliance-Dokumentation
docs/                      diese nutzerorientierte Dokumentation
examples/                  OpenAPI-Beschreibungen des SVWS-Servers (siehe svws-server-api.md)
docker/, docker-compose.yml  lokale Entwicklungsumgebung
```

Details zum lokalen Start und zu den einzelnen Tests: [Repository-Root-`README.md`](../../README.md).
