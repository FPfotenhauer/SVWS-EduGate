# ADR-006: Secret-Handling für SVWS-Zugangsdaten

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

EduGate verwahrt Zugangsdaten zu allen verwalteten SVWS-Instanzen (privilegierte Verwaltungszugänge und Schema-Zugänge für das Gateway). Diese Credentials sind das wertvollste Geheimnis des Systems: Ihre Kompromittierung entspräche dem Zugriff auf Schülerdaten aller Mandanten. Anforderungen: Verschlüsselung at rest, keine Ausgabe an Clients oder ins Frontend, kein Auftauchen in Logs, Rotierbarkeit.

## Betrachtete Optionen

1. **Anwendungsseitige Verschlüsselung in PostgreSQL (AES-256-GCM):** Credentials werden vor dem Persistieren mit einem Master-Key (Umgebungsvariable/Secret des Containers) verschlüsselt; bewährt im Vorgängerprojekt.
2. **Externes Secret-Management (OpenBao):** dediziertes Produkt mit Audit, Leasing und Rotation. Hinweis: HashiCorp Vault selbst steht seit dem Wechsel auf BUSL nicht mehr unter einer Open-Source-Lizenz; für dieses Projekt käme nur der Fork **OpenBao** infrage.
3. **PostgreSQL pgcrypto:** Verschlüsselung in der DB, aber Schlüssel wandert in SQL-Statements und damit potenziell in Logs – verworfen.

## Entscheidung

Gewählt wurde **Option 1** für Phase 1, mit klaren Invarianten:

- AES-256-GCM mit zufälligem Nonce je Datensatz; Master-Key ausschließlich als Container-Secret, nie im Repo, nie in `.env.example` mit echtem Wert.
- Key-Versionierung im Datensatz (`key_version`), dokumentiertes Re-Encrypt-Verfahren für Key-Rotation.
- Entschlüsselung nur unmittelbar vor dem SVWS-Aufruf im Speicher; Credentials erscheinen niemals in API-Responses, Logs oder dem Audit-Log.
- Getrennte Credentials je Zweck: privilegierter Verwaltungszugang (nur Control Plane) vs. Schema-Zugang (Gateway) – Least Privilege gegenüber dem SVWS-Server.

**Ausbauoption:** Bei wachsender Instanzzahl oder Audit-Anforderungen wird die Ablage hinter einem `SecretStore`-Port gekapselt auf **OpenBao** umgestellt (neues ADR). Die Port-Abstraktion wird von Beginn an eingezogen.

## Konsequenzen

### Positiv

- Kein zusätzliches Produkt in Phase 1; Sicherheit hängt an einem klar benennbaren Master-Key.
- Rotation und spätere OpenBao-Migration sind vorbereitet (Key-Version, Port-Abstraktion).

### Negativ / Risiken

- Master-Key-Verlust macht alle Credentials unbrauchbar → dokumentiertes Backup-/Recovery-Verfahren für den Key ist Pflicht.
- Schutzniveau ist an die Container-/Host-Härtung gekoppelt (BSI SYS.1.6 beachten).

## Verweise

- ADR-004 (Credential-Injection im Gateway), SVWS-Main-Server (AES-GCM-Vorarbeit)
