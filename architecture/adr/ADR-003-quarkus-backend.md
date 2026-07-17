# ADR-003: Quarkus (Java 21) als Backend-Framework

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Für Control Plane und Gateway wird ein Backend-Framework benötigt. Relevante Kräfte: Nähe zum SVWS-Server (Java-Ökosystem), vorhandene Erfahrung aus SVWS-Main-Server, Container-Betrieb im RZ, Gateway-Anforderungen (effizientes Proxying, OIDC, Observability).

## Betrachtete Optionen

1. **Quarkus (Java 21):** bewährt im Vorgängerprojekt; Extensions für REST-Client, OIDC, Health/Metrics (MicroProfile), reaktives HTTP via Vert.x; schneller Start, geringer Speicherbedarf, optional native Images.
2. **Spring Boot:** größtes Ökosystem, aber schwergewichtiger und ohne Mehrwert gegenüber vorhandener Quarkus-Erfahrung.
3. **Node.js/NestJS:** Sprachwechsel im Backend ohne fachlichen Vorteil; Java-Nähe zum SVWS-Projekt ginge verloren.

## Entscheidung

Gewählt wurde **Option 1: Quarkus mit Java 21** für beide Services, als Maven-Multi-Module-Projekt (`edugate-core`, `edugate-control-plane`, `edugate-gateway`). Frontend unverändert: **Vue 3 + TypeScript + Vite + Pinia**.

Architekturstil im Backend: Clean Architecture / Ports & Adapters (Ressourcen → Services → Domäne → Repositories/Clients), wie im Vorgängerprojekt begonnen.

## Konsequenzen

### Positiv

- Kontinuität: vorhandener Code, Muster und Erfahrung aus SVWS-Main-Server sind übertragbar.
- Quarkus-OIDC-Extension deckt Token-Validierung für beide Services ab (ADR-005).
- Health-/Readiness-Endpoints und Metriken für den RZ-Betrieb ohne Zusatzaufwand.

### Negativ / Risiken

- Java-Build-Zeiten länger als bei Node-Toolchains; abgefedert durch Quarkus Dev-Mode.
- Natives Kompilieren (GraalVM) bleibt optional und wird nur bei Bedarf evaluiert.

## Verweise

- ADR-001 (Modulschnitt), SVWS-Main-Server (Vorgängerprojekt)
