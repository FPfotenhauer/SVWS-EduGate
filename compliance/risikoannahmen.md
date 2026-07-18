# Risikoannahmen

Diese Datei sammelt fruehe Risikoannahmen fuer SVWS-EduGate. Sie wird mit der Implementierung und dem Zielbetrieb fortgeschrieben.

| Risiko | Auswirkung | Gegenmassnahme | Status |
|--------|------------|----------------|--------|
| RLS wird bei neuer Tabelle vergessen oder falsch konfiguriert. | Mandantentrennung kann verletzt werden. | RLS-Waechter-Test nach ADR-008, Review von Migrationen. | geplant |
| Operator-Zugriff wird ausserhalb des vorgesehenen Pfads verwendet. | Mandantenuebergreifende Zugriffe koennen unkontrolliert entstehen. | `OperatorAccess`, zweite Datasource nur dort, ArchUnit-Test, Audit-Pflicht nach ADR-009. | geplant |
| Fehler-Audit geht durch Rollback verloren. | Kritische Fehlversuche sind nicht nachvollziehbar. | `ERROR`-/`DENIED`-Audit in eigener Transaktion, zusaetzlich strukturiertes Log. | geplant |
| SVWS-Zugangsdaten werden versehentlich ausgegeben oder geloggt. | Kompromittierung von Schulverwaltungsdaten. | SecretStore-Port, AES-GCM, keine Secrets in Responses/Logs, Tests und Code-Review. | geplant |
| Master-Key geht verloren. | Verschluesselte Credentials sind nicht mehr nutzbar. | Backup-/Recovery-Verfahren und Key-Rotation dokumentieren. | offen |
| Keycloak-Fehlkonfiguration ermoeglicht zu breite Rollen oder Scopes. | Unberechtigter Zugriff auf Admin- oder Gateway-Funktionen. | Realm-Review, 401/403-Tests, kurze Token-Lebensdauer fuer Clients. | geplant |
| Gateway-Eigenbau enthaelt Auth- oder Routingfehler. | API-Clients koennen falsche Daten abrufen. | Scope-Tests, Contract-Tests gegen SVWS-API, Audit, spaeter Security-Review. | offen |
| Audit-Log waechst unkontrolliert. | Speicher- und Betriebsprobleme. | Aufbewahrungs-/Verdichtungskonzept spaeter per ADR festlegen. | offen |

## Review-Regel

Neue Risiken werden ergaenzt, sobald eine Architekturentscheidung, Migration, Schnittstelle oder Betriebsannahme eine neue Angriffsflaeche oder einen neuen Ausfallmodus einfuehrt.
