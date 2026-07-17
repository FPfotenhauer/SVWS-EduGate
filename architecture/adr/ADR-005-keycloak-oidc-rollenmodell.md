# ADR-005: Keycloak (OIDC) und Rollen-/Scope-Modell

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

Benötigt werden Authentifizierung und Autorisierung für zwei Nutzergruppen: menschliche Admins (Control Plane, Browser-Login) und maschinelle API-Clients (Gateway). Der Betrieb erfolgt im BSI-zertifizierten RZ; externe Identity-Dienste (Cloud-IdPs) scheiden aus. Das Berechtigungsmodell muss die Mandantenhierarchie abbilden.

## Betrachtete Optionen

1. **Keycloak, selbst gehostet:** OIDC/OAuth2-Standard, Realm-/Rollen-/Client-Scope-Modell, on-premises, in der öffentlichen Verwaltung etabliert; Quarkus-OIDC-Integration vorhanden.
2. **Eigene JWT-Ausgabe im Backend** (wie im Vorgängerprojekt): weniger Infrastruktur, aber Eigenbau von Token-Lifecycle, Client-Verwaltung und Admin-Login – genau die Fehlerquellen, die ein IdP vermeidet.
3. **Andere selbst gehostete IdPs (z. B. Authentik, ZITADEL):** möglich, aber ohne Vorteil gegenüber der Verbreitung und Dokumentationslage von Keycloak.

## Entscheidung

Gewählt wurde **Option 1: Keycloak** in einem eigenen Realm `edugate`.

**Rollen (Menschen, Authorization-Code-Flow + PKCE):**

| Rolle | Rechte |
|-------|--------|
| `dienstleister-admin` | Vollzugriff auf alle Mandanten (auditiert, vgl. ADR-002 Bypass-Rolle). |
| `schultraeger-admin` | Sicht/Verwaltung nur des eigenen Schulträgers (spätere Ausbaustufe; Attribut `tenant_id` im Token). |

**API-Clients (Maschinen, Client-Credentials-Flow):**

- Ein Keycloak-Client je konsumierendem System.
- Scopes nach dem Muster `schule:{schulnummer}:read` bzw. `schultraeger:{traegernummer}:read`; Schreib-Scopes existieren in Phase 1 nicht.
- Das Gateway prüft die Token-Signatur offline (JWKS, gecacht) und gleicht Scope gegen den angefragten Pfad ab – ein Scope-Mismatch ergibt 403 plus Audit-Eintrag.

**Betrieb:** Keycloak liegt in der Verwaltungszone (ADR-007); Token-Lebensdauern kurz (Access-Token ≤ 5 min für Clients), Ausstellung nur über TLS nach BSI TR-02102-2.

## Konsequenzen

### Positiv

- Standardprotokolle statt Eigenbau; Client-Onboarding/Offboarding zentral in Keycloak.
- Mandantenbezug ist kryptografisch im Token verankert, nicht in Request-Parametern.
- Vorbereitet auf spätere externe Clients und auf Schulträger-Self-Service.

### Negativ / Risiken

- Zusätzliche Komponente in Betrieb und Backup (Keycloak-DB).
- Scope-Explosion bei sehr vielen Schulen möglich → bei Bedarf Gruppen-Scopes je Schulträger, in neuem ADR zu präzisieren.

## Verweise

- ADR-002 (Tenant-Kontext), ADR-004 (Gateway-Pipeline), ADR-007 (Zonen)
