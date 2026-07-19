# ADR-018: Exportformat für Verbindungsdaten von Schuldatenbanken

- **Status:** proposed
- **Datum:** 2026-07-19
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate verwaltet Schuldatenbanken/Schemata auf SVWS-Instanzen (ADR-012/ADR-013) und soll
perspektivisch auch API-Zugänge für diese Schuldatenbanken betreiberfreundlich verwalten
(ADR-015). Aktuell benötigt ein Schul-Schema typischerweise noch einen Benutzer mit passenden
Berechtigungen für die SVWS-External-API, heute meist per BasicAuth. Perspektivisch soll dieses
Modell in Richtung API-Token weiterentwickelt werden.

Betreiber werden solche Zugangsdaten nicht nur in EduGate sehen oder setzen wollen, sondern in
vorhandene Secret-Infrastruktur übernehmen müssen: HashiCorp Vault, Kubernetes Secrets bzw.
External Secrets, 1Password/Bitwarden Secrets Manager, Ansible-/GitOps-Prozesse oder eigene
Rechenzentrums-Vaults. Dafür braucht EduGate ein maschinenlesbares, versioniertes und
verschlüsselbares Exportformat für die Verbindungsdaten von Schuldatenbanken.

Das Exportformat muss mehrere Anforderungen ausbalancieren:

- Es muss Schulträger, Schule, Umgebung, SVWS-Instanz, Schema und Authentifizierungsart eindeutig
  beschreiben.
- Es muss heutige BasicAuth-Zugangsdaten und spätere API-Token abbilden können.
- Es darf kein proprietäres Kryptoverfahren erfinden.
- Es muss sich gut in gängige Vault- und GitOps-Werkzeuge importieren lassen.
- Es muss privilegierte SVWS-/Root-/Betreiber-Credentials strikt von schulbezogenen
  External-API-Verbindungsdaten trennen.
- Es muss auditierbar exportiert werden können, ohne Secret-Werte in Audit, UI oder Logs
  preiszugeben.

## Betrachtete Optionen

1. **Unverschlüsseltes JSON/YAML:** sehr einfach zu erzeugen und zu importieren, aber als
   Austauschformat für Secrets ungeeignet. Nur akzeptabel als kurzlebiger Klartext nach expliziter
   Entschlüsselung in einer gesicherten Pipeline.
2. **CSV oder `.env`-Dateien:** für Menschen und einfache Skripte bequem, aber zu flach für
   Schulträger-/Schule-/Schema-/Instanz-Metadaten, schwer versionierbar und fehleranfällig bei
   komplexeren Authentifizierungsarten.
3. **Passwortgeschützte ZIP-/Archivdateien:** verbreitet, aber kryptografisch und betrieblich
   uneinheitlich. Schlecht automatisierbar für Vault-Imports und mit hohem Risiko für
   Schattenprozesse.
4. **JWE/COSE als standardisiertes verschlüsseltes JSON-Artefakt:** technisch sauber und
   standardisiert, aber in typischen Betreiber-/GitOps-Werkzeugen weniger direkt nutzbar als
   SOPS-Dateien.
5. **SOPS-verschlüsseltes YAML/JSON mit age/KMS/PGP:** im DevOps- und GitOps-Umfeld verbreitet,
   maschinenlesbar, gut reviewbar ohne Klartext-Secrets, automatisierbar und kompatibel mit
   gängigen Importpfaden in Vaults und Secret-Manager.

## Entscheidung

Vorläufig gewählt wird **SOPS-verschlüsseltes YAML oder JSON mit `age` als Standard-
Empfängermechanismus**. Der unverschlüsselte Inhalt folgt einem **versionierten, JSON-kompatiblen
EduGate-Schema**. Weitere SOPS-Empfänger wie PGP oder KMS können später als Betreiberoption ergänzt
werden.

Das Standardartefakt heißt konzeptionell:

```text
edugate-db-connections.v1.sops.yaml
```

Alternativ kann derselbe Inhalt als JSON exportiert werden:

```text
edugate-db-connections.v1.sops.json
```

YAML ist für Betreiberreview und GitOps angenehmer, JSON ist für API-/Vault-Importe oft direkter.
Beide Varianten müssen dasselbe logische Schema abbilden.

### Kanonisches Inhaltsmodell

Der Klartext vor SOPS-Verschlüsselung ist ein Bundle mit Metadaten und einer Liste von
Schuldatenbank-Verbindungen:

```yaml
apiVersion: edugate.svws.nrw/v1
kind: SchoolDatabaseConnectionBundle
metadata:
  exportedAt: "2026-07-19T12:00:00Z"
  exportedBy: "admin@edugate.local"
  sourceSystem: "SVWS-EduGate"
  bundleId: "00000000-0000-0000-0000-000000000000"
items:
  - id: "00000000-0000-0000-0000-000000000000"
    schultraeger:
      id: "00000000-0000-0000-0000-000000000000"
      name: "Stadt Beispiel"
      traegernummer: "..."
    schule:
      id: "00000000-0000-0000-0000-000000000000"
      schulnummer: "123456"
      name: "Beispielschule"
    umgebung: "PRODUKTIV"
    svwsInstanz:
      id: "00000000-0000-0000-0000-000000000000"
      name: "svws-prod-01"
      baseUrl: "https://svws-prod-01.example.org"
    schema:
      name: "123456"
      status: "AKTIV"
    connection:
      type: "svws-external-api"
      baseUrl: "https://svws-prod-01.example.org"
      schema: "123456"
      auth:
        type: "basic"
        username: "api_123456"
        password: "..."
```

Für die spätere Token-Welt bleibt die Struktur stabil; nur `auth.type` und die Secret-Felder ändern
sich:

```yaml
auth:
  type: "api-token"
  token: "..."
  tokenHint: "svws-edugate-123456-prod"
```

Zulässige Authentifizierungsarten werden versioniert erweitert, z. B.:

- `basic` für heutige SVWS-External-API-Zugänge,
- `api-token` für spätere SVWS-/EduGate-Token,
- perspektivisch weitere Verfahren, falls ADR-015 sie vorsieht.

### Zielpfade für Vault-Importe

Das Format selbst legt keinen einzelnen Vault-Anbieter fest. Ein Importwerkzeug kann aber aus den
Metadaten stabile Secret-Pfade ableiten, z. B.:

```text
edugate/schultraeger/<traegernummer>/schulen/<schulnummer>/<umgebung>/svws-external-api
```

Betreiber dürfen eigene Pfadkonventionen definieren. EduGate sollte die Exportdaten deshalb nicht
nur als Baumstruktur, sondern mit allen notwendigen IDs und Namen liefern, damit verschiedene
Vault-Konventionen möglich bleiben.

### Sicherheitsregeln

Für den Export gelten folgende Mindestregeln:

- SOPS-Export ist der Standard; unverschlüsselter Export ist nur als explizite, besonders
  bestätigte Betreiberaktion oder für interne Pipeline-Zwischenschritte zulässig.
- Secret-Werte erscheinen nie in Audit-Details, Serverlogs oder UI-Bestätigungen.
- Das Audit hält nur fest, dass ein Export stattgefunden hat: Zeitpunkt, Admin-Subject,
  Filterumfang, Anzahl exportierter Einträge, Empfänger-/Key-IDs, aber keine Secret-Werte.
- Exportberechtigung ist eine eigene Betreiberrolle bzw. Berechtigung aus ADR-015, nicht
  automatisch jede allgemeine Schreibberechtigung auf Schuldatenbanken.
- Privilegierte SVWS-Root-/Privileged-API-Credentials, MariaDB-Admin-Zugänge,
  Backup-/Restore-Secrets und EduGate-Master-Keys gehören nicht in dieses Bundle.
- Exportdateien dürfen nicht dauerhaft in EduGate gespeichert werden, sofern kein separates
  Betreiberkonzept für verschlüsselte Artefaktablage existiert.

### Einordnung zu EduGates internem Secret-Handling

ADR-006 beschreibt, wie EduGate SVWS-Zugangsdaten intern verschlüsselt speichert. ADR-018 ersetzt
dieses interne Secret-Handling nicht. Der Export ist ein kontrollierter Übergabemechanismus aus
EduGate heraus in Betreiber-Vaults oder Automatisierungspipelines.

Die SOPS-Verschlüsselung schützt das Transport-/Austauschartefakt. Nach dem Import in ein Vault ist
das jeweilige Vault für Speicherung, Rotation, Zugriffskontrolle und Audit der importierten Secrets
verantwortlich.

## Konsequenzen

### Positiv

- Betreiber erhalten ein gängiges, automatisierbares und verschlüsseltes Austauschformat.
- EduGate muss kein eigenes Kryptoprotokoll definieren.
- Das Format passt zu GitOps, CI/CD und Vault-Importen.
- BasicAuth und spätere API-Token können im selben versionierten Modell abgebildet werden.
- Schul-, Schema-, Instanz- und Umgebungsbezug bleiben auch nach dem Import nachvollziehbar.
- Privilegierte Betreiber-/Root-Secrets werden bewusst aus dem Schuldatenbank-Verbindungsbundle
  herausgehalten.

### Negativ / Risiken

- Betreiber müssen SOPS/age oder kompatible Entschlüsselungsprozesse betreiben können.
- Für manche Vault-Produkte braucht es zusätzliche Importadapter oder Skripte.
- Das Format transportiert Secrets; Fehlbedienung bleibt trotz Verschlüsselung ein hohes Risiko.
- Secret-Rotation ist mit dem Exportformat noch nicht gelöst, sondern muss in ADR-015 bzw. späteren
  Credential-Workflows präzisiert werden.
- Mehrere gleichzeitige Zielkonventionen für Vault-Pfade können Betreiber verwirren, wenn EduGate
  keine klaren Empfehlungen und Beispiele liefert.

## Verweise

- ADR-006 (Secret-Handling für SVWS-Zugangsdaten)
- ADR-012 (Schemaverwaltung und Schuldatenbanken)
- ADR-013 (Betreiber-UI für Schemaverwaltung und Schuldatenbanken)
- ADR-015 (Rollen- und Rechte-Management)
- ADR-016 (Deployment- und Auslieferungsmodell)
- ADR-017 (Backup-Konzept für Schulschemata)
- SOPS: <https://github.com/getsops/sops>
- age: <https://age-encryption.org/>
