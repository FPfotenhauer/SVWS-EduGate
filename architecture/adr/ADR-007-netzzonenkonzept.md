# ADR-007: Netzzonenkonzept und externe Ausbaustufe

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Der Betrieb erfolgt in BSI-zertifizierten Rechenzentren; BSI IT-Grundschutz NET.1.1 verlangt eine segmentierte Netzarchitektur. Phase 1 ist rein intern, aber die spätere externe Exponierung des Gateways ist gesetzt. Wird die Zonierung erst beim Übergang „intern → extern“ eingeführt, droht ein Umbau der gesamten Verkehrsführung.

## Betrachtete Optionen

1. **Flaches internes Netz in Phase 1,** Zonierung erst bei externer Öffnung: geringster Startaufwand, aber genau der Umbau, der vermieden werden soll.
2. **Zonenmodell von Beginn an,** externe Zone zunächst leer: minimal höherer Startaufwand (Docker-Netze/Firewall-Regeln), Übergang zur Ausbaustufe ist rein additiv.

## Entscheidung

Gewählt wurde **Option 2** mit vier Zonen:

| Zone | Inhalt | Erreichbarkeit |
|------|--------|----------------|
| **Zone 1 – Verwaltungsnetz** | Control Plane, Admin-SPA, Keycloak, PostgreSQL | Nur internes RZ-/Admin-Netz. Wird niemals extern exponiert. |
| **Zone 2 – Gateway-Zone** | Gateway-Service | Phase 1: nur aus dem internen Netz. Ausbaustufe: aus Zone 3. |
| **Zone 3 – Extern (Ausbaustufe)** | Reverse Proxy / WAF | Initial nicht vorhanden; einziger Eintrittspunkt von außen, terminiert TLS 1.3 und leitet ausschließlich an Zone 2 weiter. |
| **Zone 4 – SVWS-Zone** | SVWS-Instanzen + MariaDB je Schulträger | Nur erreichbar aus Zone 1 (Control Plane) und Zone 2 (Gateway), idealerweise per mTLS. Kein Weg von Zone 3/extern direkt in Zone 4. |

Verkehrsregeln (Whitelist-Prinzip, alles andere verboten):

- Zone 1 → Zone 4 (Verwaltung, Sync), Zone 1 ↔ Keycloak/PostgreSQL intern.
- Zone 2 → Zone 4 (Datenabruf), Zone 2 → Zone 1 nur für Keycloak-JWKS und read-only PostgreSQL.
- Zone 3 → Zone 2 (erst in der Ausbaustufe).
- MariaDB der SVWS-Instanzen ist ausschließlich für den jeweiligen SVWS-Server erreichbar – EduGate spricht nie direkt mit MariaDB.

TLS überall nach BSI TR-02102-2; interne Strecken (Zone 1/2 → Zone 4) mit mTLS, sobald die SVWS-Instanzen entsprechend konfiguriert sind. In der lokalen Entwicklung werden die Zonen als getrennte Docker-Compose-Netze nachgebildet, damit Fehlkonfigurationen früh auffallen.

## Konsequenzen

### Positiv

- Übergang „intern → extern“ ist das Dazuschalten von Zone 3 plus Firewall-Regel – kein Architekturumbau.
- Verwaltung, IdP und Datenbank sind konstruktiv von jeder externen Erreichbarkeit ausgeschlossen.
- Direkte Entsprechung zu BSI NET.1.1; erleichtert Sicherheitskonzept und Audit.

### Negativ / Risiken

- Mehr Netz-Konfiguration bereits in der Entwicklung (mehrere Compose-Netze).
- mTLS zu SVWS-Instanzen hängt von deren Zertifikatskonfiguration ab → bis dahin TLS + IP-Restriktion.

## Verweise

- ADR-001 (Service-Schnitt), ADR-004 (Gateway), ARCHITECTURE.md Kap. 7 (Verteilungssicht)
