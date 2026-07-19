# ADR-015: Rollen- und Rechte-Management für Betreiber und API-Gateway

- **Status:** proposed
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

ADR-005 legt Keycloak als OIDC-Provider fest und unterscheidet menschliche Admins
(Control Plane) von maschinellen API-Clients (Gateway). Der bisherige Stand reicht für den ersten
Durchstich: `dienstleister-admin` hat weitreichende Verwaltungsrechte, API-Clients erhalten
Client-Credentials-Tokens mit Scopes wie `schule:{schulnummer}:read`.

Mit Schemaverwaltung, echter SVWS-Privileged-API-Anbindung und späterem Gateway-Betrieb wird das
zu grob. Betreiber brauchen abgestufte Rollen: nicht jede Person, die Instanzen sehen darf, soll
Credentials exportieren, Migrationen starten, Zertifikate ersetzen oder API-Client-Rechte
vergeben können. Gleichzeitig darf das API-Gateway nicht aus Instanzzugehörigkeit allein ableiten,
welche Schuldaten ein externer Client lesen darf. Maßgeblich bleibt die Kette aus ADR-012:

```text
Tenant/Schulträger -> Schule -> Schema -> SVWS-Instanz
```

Die Rechteverwaltung muss daher zwei Welten verbinden:

- menschliche Betreiberrollen für Administration und gefährliche Operationen,
- maschinelle Gateway-Berechtigungen für externe Systeme mit fein begrenztem Lesezugriff.

## Betrachtete Optionen

1. **Alle Rechte ausschließlich in Keycloak modellieren:** Keycloak bleibt zentrale Stelle, aber
   feingranulare Rechte je Schule/Schema/Umgebung/API-Client würden zu Rollen- und Scope-Explosion
   führen. Fachliche Prüfungen müssten trotzdem in EduGate erfolgen.
2. **Alle Rechte ausschließlich in EduGate modellieren:** maximale fachliche Kontrolle, aber
   Authentifizierung, Token-Lifecycle und Client-Credentials würden unnötig selbst gebaut.
3. **Hybrides Modell:** Keycloak übernimmt Authentifizierung, Token-Ausgabe und grobe Rollen bzw.
   technische Clients. EduGate speichert fachliche Berechtigungen, Zuordnungen, API-Client-Grants
   und prüft diese im Control Plane Backend bzw. Gateway.

## Entscheidung

Vorläufig gewählt wird **Option 3**.

Keycloak bleibt Identity Provider und Token-Aussteller. EduGate wird die fachliche
Berechtigungsinstanz für Betreiberaktionen und Gateway-Zugriffe.

### Menschliche Betreiberrollen

Die genaue Rollennamensliste wird später verfeinert. Als Leitbild gelten:

| Rolle | Zweck |
|-------|-------|
| `dienstleister-admin` / `betreiber-admin` | Vollzugriff auf Betreiberverwaltung, Rollen, Instanzen, Credentials und gefährliche Operationen. |
| `betreiber-operator` | Operative Verwaltung von Instanzen, Schulen und Schuldatenbanken; gefährliche Operationen nur, wenn ausdrücklich erlaubt. |
| `credential-admin` | Verwaltung, Rotation und Export von Credential-/API-Zugriffen; besonders streng auditiert. |
| `migration-operator` | Durchführung vorbereiteter Migrations-/Import-Workflows, aber keine allgemeine Rollenverwaltung. |
| `audit-reader` | Lesender Zugriff auf Audit- und Betriebsnachweise ohne Secret-Anzeige. |
| `schultraeger-admin` | Spätere Ausbaustufe: Sicht/Verwaltung nur des eigenen Schulträgers; keine Betreiberressourcen wie globale SVWS-Instanzen verwalten. |

Diese Rollen sind bewusst noch nicht als endgültige Keycloak-Rollen festgeschrieben. ADR-015 legt
das Prinzip fest: gefährliche Fähigkeiten werden getrennt und auditierbar vergeben, statt alles
unter `dienstleister-admin` zu verstecken.

### Berechtigung auf gefährliche Operationen

Operationen aus ADR-014 brauchen eigene Berechtigungen, z. B.:

- Schema auf SVWS-Server anlegen,
- Schema deaktivieren/reaktivieren,
- Migration starten,
- Import/Export starten,
- Credential erzeugen/rotieren/exportieren,
- Zertifikat erzeugen/importieren/ersetzen,
- Schema löschen oder Destroy ausführen.

Die UI darf Buttons nicht nur ausblenden, sondern das Backend muss jede Operation serverseitig
prüfen. Jede Berechtigungsentscheidung wird so gebaut, dass sie testbar und auditierbar ist.

### API-Gateway und maschinelle Clients

Für externe Systeme bleibt der Client-Credentials-Flow aus ADR-005 der Ausgangspunkt. Keycloak
stellt Tokens aus; das Gateway prüft Signatur, Ablauf und grobe Scopes. Die fachliche Freigabe,
welche Schule, welches Schema oder welcher Schulträger gelesen werden darf, wird in EduGate als
Gateway-Grant modelliert.

Leitregeln:

- Ein API-Client erhält nur die minimal benötigten Leserechte.
- Schreibzugriffe über das Gateway sind nicht Teil der ersten Ausbaustufe.
- Grants können auf Schulträger-, Schul- oder Schemaebene liegen; die genaue Granularität wird
  nach API-Bedarf verfeinert.
- Das Gateway leitet nur auf die SVWS-External-API weiter, nicht auf Server- oder Privileged-API.
- Gateway-Rechte werden niemals allein aus `svws_instanz` abgeleitet.
- Aktivierung, Deaktivierung und Änderung von API-Client-Grants sind auditpflichtig.

### BasicAuth zu API-Token

Aktuell benötigt ein Schul-Schema im SVWS-Server Benutzer mit passenden Rechten für External-API
Zugriffe, heute typischerweise per BasicAuth. Perspektivisch soll dies auf API-Token umgestellt
werden. EduGate modelliert deshalb nicht "BasicAuth-Benutzer" als dauerhaftes UI- und
Datenmodell, sondern abstrakt **API-Zugriffe/Credentials**:

- heutige BasicAuth-Zugangsdaten,
- spätere API-Token,
- Status, Zweck, Schema/Schule, letzter Test, letzte Rotation,
- Export/Anzeige nur als explizite, auditierte Secret-Aktion.

So kann die technische SVWS-Authentifizierung wechseln, ohne dass EduGate eine zweite
Rechteverwaltung parallel aufbauen muss.

## Konsequenzen

### Positiv

- Keycloak bleibt Standard-IdP, EduGate behält aber die fachliche Kontrolle.
- Betreiberrollen können nach Risiko und Aufgabe getrennt werden.
- Gateway-Rechte bleiben mandanten- und schemaorientiert statt instanzorientiert.
- Die spätere Umstellung von BasicAuth auf API-Token wird vorbereitet.

### Negativ / Risiken

- Hybride Rechteverwaltung ist komplexer als "alles in Keycloak".
- Es braucht klare Administrationsoberflächen, damit Rollen, Grants und Credentials nicht
  unverständlich werden.
- Rechteprüfungen müssen konsequent in Backend und Gateway umgesetzt und getestet werden; reine
  UI-Logik reicht nicht.

## Verweise

- ADR-002 (Mandantenmodell mit PostgreSQL RLS)
- ADR-004 (API-Gateway als eigener Quarkus-Service)
- ADR-005 (Keycloak und Rollen-/Scope-Modell)
- ADR-009 (Operator-Zugriff und Audit)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-013 (Betreiber-UI für Schemaverwaltung)
- ADR-014 (Verwendung echter SVWS-Privileged-API-Aufrufe)
