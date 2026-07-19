# Architekturentscheidungen (ADRs)

Dieser Ordner enthält alle Architecture Decision Records für SVWS-EduGate im [MADR](https://adr.github.io/madr/)-angelehnten Format. Neue ADRs werden fortlaufend nummeriert und ändern einmal getroffene Entscheidungen nicht nachträglich – eine revidierte Entscheidung erhält ein neues ADR, das das alte auf `superseded` setzt.

## Index

| ADR | Titel | Status |
|-----|-------|--------|
| [ADR-001](./ADR-001-trennung-control-plane-data-plane.md) | Trennung von Control Plane und Data Plane | accepted |
| [ADR-002](./ADR-002-mandantenmodell-postgresql-rls.md) | Mandantenmodell mit PostgreSQL Row-Level Security | accepted |
| [ADR-003](./ADR-003-quarkus-backend.md) | Quarkus (Java 21) als Backend-Framework | accepted |
| [ADR-004](./ADR-004-api-gateway-eigenbau.md) | API-Gateway als eigener Quarkus-Service | accepted |
| [ADR-005](./ADR-005-keycloak-oidc-rollenmodell.md) | Keycloak (OIDC) und Rollen-/Scope-Modell | accepted |
| [ADR-006](./ADR-006-secret-handling-svws-credentials.md) | Secret-Handling für SVWS-Zugangsdaten | accepted |
| [ADR-007](./ADR-007-netzzonenkonzept.md) | Netzzonenkonzept und externe Ausbaustufe | accepted |
| [ADR-008](./ADR-008-rls-wurzeltabelle-schultraeger.md) | RLS-Behandlung der Mandanten-Wurzeltabelle `schultraeger` | accepted |
| [ADR-009](./ADR-009-operator-zugriff-und-audit.md) | Operator-Zugriff und Audit-Modell für Admin-Operationen | accepted |
| [ADR-010](./ADR-010-einheitliches-design-system.md) | Einheitliches Design-System für SVWS-Apps (Emerald) | accepted |
| [ADR-011](./ADR-011-svws-instanz-als-geteilte-betriebsressource.md) | SVWS-Instanz als geteilte, mandantenübergreifende Betriebsressource | accepted |
| [ADR-012](./ADR-012-schemaverwaltung-und-schuldatenbanken.md) | Schemaverwaltung und Schuldatenbanken | accepted |
| [ADR-013](./ADR-013-betreiber-ui-schemaverwaltung.md) | Betreiber-UI für Schemaverwaltung und Schuldatenbanken | accepted |

## Neues ADR anlegen

1. [`ADR-TEMPLATE.md`](./ADR-TEMPLATE.md) kopieren und fortlaufend nummerieren.
2. Status zunächst `proposed`, nach Entscheidung `accepted`.
3. In dieser Tabelle verlinken.
