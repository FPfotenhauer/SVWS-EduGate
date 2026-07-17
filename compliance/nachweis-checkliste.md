# Nachweis-Checkliste

Diese Checkliste sammelt pruefbare Artefakte, die fuer Sicherheits- und Compliance-Nachweise relevant sind. `Status` startet als `geplant` und wird mit konkreten Links oder Testergebnissen fortgeschrieben.

| Nachweis | Quelle / Artefakt | Status |
|----------|-------------------|--------|
| RLS ist auf allen Tenant-Tabellen aktiv und erzwungen. | Testcontainers RLS-Waechter-Test nach ADR-008. | geplant |
| `schultraeger` ist ueber Policy auf eigene `id` geschuetzt. | RLS-Waechter-Test, Migration `V1__initial_schema.sql`. | geplant |
| Cross-Tenant-Zugriffe liefern keine fremden Daten. | Integrationstest mit Tenant-Kontext A/B. | geplant |
| Operator-Datasource wird nur ueber `OperatorAccess` verwendet. | ArchUnit-Test. | geplant |
| Jede Operator-Operation erzeugt genau einen Admin-Audit-Eintrag. | Integrationstest fuer `audit_admin`. | geplant |
| Fehler-Audit ueberlebt Rollback der Fachtransaktion. | Integrationstest mit provoziertem Fehler und `outcome = ERROR`. | geplant |
| `audit_admin` ist append-only fuer Anwendungsrollen. | DB-Rechte-Test: `UPDATE`/`DELETE` werden verweigert. | geplant |
| Requests ohne Token ergeben 401 als Problem-JSON. | RestAssured-Test. | geplant |
| Requests ohne passende Rolle ergeben 403 als Problem-JSON. | RestAssured-Test. | geplant |
| SVWS-Credentials werden verschluesselt abgelegt. | SecretStore-Unit-Tests, ADR-006. | geplant |
| Manipulierte Chiffrate werden erkannt. | AES-GCM-Manipulationstest. | geplant |
| Keine echten Secrets im Repository. | `git grep`/Secret-Scan, `.env.example` nur mit Platzhaltern. | geplant |
| Frontend speichert Access-Token nicht in `localStorage`. | Code-Review, Frontend-Test/Static Check falls sinnvoll. | geplant |
| Docker Compose exponiert PostgreSQL nicht nach aussen. | Review `docker-compose.yml`. | geplant |
| Healthchecks fuer relevante Services vorhanden. | Compose-Datei und `/q/health/*`-Tests. | geplant |

## Spaetere Betriebsnachweise

- Backup- und Restore-Test fuer PostgreSQL.
- Backup-/Recovery-Verfahren fuer `EDUGATE_MASTER_KEY`.
- Patch-/Updateprozess fuer Container-Images.
- Aufbewahrungs- und Loeschkonzept fuer Audit-Daten.
- Review der produktiven Firewall-Regeln gegen ADR-007.
