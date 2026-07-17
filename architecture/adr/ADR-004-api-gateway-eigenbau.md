# ADR-004: API-Gateway als eigener Quarkus-Service (Eigenbau mit Austauschoption)

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

API-Clients sollen Schuldaten über die SVWS-API abrufen – sicher, mandantengetrennt und auditierbar. Die Kernaufgaben des Gateways sind dabei fachlich, nicht generisch: Token-/Scope-Prüfung entlang der Mandantenhierarchie, Auflösung Schule → Schema → Instanz, Injection der intern verwahrten SVWS-Credentials, Audit-Logging. Phase 1 ist rein intern mit überschaubarer Client-Zahl.

## Betrachtete Optionen

1. **Fertigprodukt (Kong OSS, Apache APISIX, Envoy):** ausgereiftes Rate-Limiting und Plugin-Ökosystem, aber die fachliche Kernlogik (Mandanten-Auflösung, Credential-Injection gegen PostgreSQL) müsste ohnehin als Custom-Plugin/Service entstehen; zusätzliches Produkt im BSI-Betrieb (Härtung, Patching, Doku).
2. **Eigener schlanker Quarkus-Service:** volle Kontrolle über die fachliche Logik, homogener Stack (ADR-003), kein zusätzliches Betriebsprodukt; Standardfunktionen (Rate-Limiting, Caching) müssen selbst ergänzt werden, wenn nötig.
3. **Gateway-Logik in die Control Plane integrieren:** verworfen, siehe ADR-001.

## Entscheidung

Gewählt wurde **Option 2**: Das Gateway ist ein eigener Quarkus-Service mit folgender Request-Pipeline:

1. Token-Signaturprüfung (Keycloak-JWKS, gecacht) und Scope-Auswertung gegen den Request-Pfad.
2. Mandanten-Auflösung (Schulnummer → Schule → Schema → SVWS-Instanz) über read-only DB-Zugriff.
3. Weiterleitung an die SVWS-API mit den intern verwahrten Instanz-Credentials (Authorization-Header; Credentials verlassen niemals das Gateway, siehe ADR-006).
4. Antwort-Durchleitung mit Timeout und Circuit-Breaker je Instanz.
5. Audit-Log-Eintrag (Client, Scope, Mandant, Pfad, Ergebnisstatus, Zeitstempel).

**Austauschoption:** Die Gateway-eigene API (`/gateway/api/v1/...`) wird stabil und produktneutral gehalten. Sollten externe Exponierung und Client-Zahl später Rate-Limiting, Quotas oder ein Plugin-Ökosystem erfordern, kann ein Fertigprodukt **vor** das Gateway geschaltet oder das Gateway ersetzt werden, ohne die Client-Verträge zu brechen. Diese Entscheidung wäre in einem neuen ADR zu dokumentieren.

## Konsequenzen

### Positiv

- Kein zusätzliches Produkt im BSI-Scope; ein Stack, ein Build, ein Deployment-Muster.
- Fachliche Logik dort, wo das Domänenwissen liegt; einfache Tests gegen das Mandantenmodell.
- Klarer Migrationspfad, falls die Anforderungen wachsen.

### Negativ / Risiken

- Querschnittsfunktionen (Rate-Limiting, Response-Caching) sind bei Bedarf selbst zu bauen.
- Sicherheitskritischer Code in Eigenverantwortung → Pflicht zu Security-Review und Tests der Auth-Pipeline.

## Verweise

- ADR-001, ADR-005, ADR-006, ADR-007
