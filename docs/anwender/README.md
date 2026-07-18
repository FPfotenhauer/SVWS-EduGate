# Anwender

Zielgruppe: spätere fachliche oder technische Nutzer der freigegebenen Funktionen von
SVWS-EduGate.

## Was Anwender von EduGate erwarten können

SVWS-EduGate stellt zwei unterschiedliche Zugänge bereit:

- Eine **Admin-Oberfläche** für die Verwaltung der Mandantenhierarchie durch
  Dienstleister-Admins (siehe [`administration/README.md`](../administration/README.md)).
- Ein **Gateway** für den gesicherten, mandantengetrennten Zugriff auf Schuldaten über die
  SVWS-Server-API.

Anwender im Sinne dieser Seite sind in erster Linie Nutzer des Gateways: Systeme oder Personen,
die über eine gesicherte Schnittstelle Schuldaten einer oder mehrerer Schulen abrufen, ohne
selbst Verwaltungsrechte für Schulträger, Schulen oder SVWS-Instanzen zu benötigen.

## Unterschied Admin-Oberfläche und Gateway-Nutzung

| | Admin-Oberfläche (Control Plane) | Gateway-Nutzung (Data Plane) |
|---|---|---|
| Nutzer | Dienstleister-Admin (Mensch, Browser-Login) | API-Client (Maschine-zu-Maschine) |
| Zugriff | Verwaltung von Schulträgern, Schulen, SVWS-Instanzen, Schemas | Lesender Zugriff auf Schuldaten einer freigegebenen Schule |
| Authentifizierung | Login über Keycloak (Browser) | Client-Credentials-Flow über Keycloak, eng begrenzter Scope je Schule |
| Reichweite | Mandantenübergreifend (auditiert) | Nur die im Token freigegebene(n) Schule(n) |

Die Zugangsdaten der SVWS-Server selbst verlassen dabei zu keinem Zeitpunkt das Backend – Clients
erhalten ausschließlich ihr eigenes, eng begrenztes OIDC-Token (siehe
[`architecture/ARCHITECTURE.md`](../../architecture/ARCHITECTURE.md), Kapitel 6).

## Ausblick: spätere Ausbaustufen

Die folgenden Punkte sind bewusst **nicht** Teil des aktuellen Stands, sondern spätere
Ausbaustufen:

- **Externe API-Clients** – Phase 1 ist auf das interne Netz des Rechenzentrums beschränkt;
  externe Exponierung ist als eigene Netzzone vorbereitet, aber noch nicht aktiv (siehe
  [ADR-007](../../architecture/adr/ADR-007-netzzonenkonzept.md)).
- **Schulträger-Self-Service** – eine eigene Sicht für Schulträger-Admins auf ihre eigenen
  Schulen ist vorgesehen, aber noch nicht umgesetzt (siehe
  [ADR-005](../../architecture/adr/ADR-005-keycloak-oidc-rollenmodell.md)).

Der jeweils aktuelle Implementierungsstand steht im
[Repository-Root-`README.md`](../../README.md).
