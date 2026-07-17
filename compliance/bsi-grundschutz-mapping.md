# BSI-Grundschutz-Mapping

Dieses Mapping ordnet die fuer SVWS-EduGate naheliegenden BSI-Grundschutz-Themen den vorhandenen Architekturentscheidungen und geplanten Nachweisen zu. Es ist ein Startpunkt und muss mit dem Zielbetrieb abgeglichen werden.

| Bereich | Relevanz fuer EduGate | Architekturbezug | Geplante Nachweise |
|---------|------------------------|------------------|--------------------|
| Netzarchitektur / Segmentierung | Trennung von Verwaltungsnetz, Gateway-Zone, externer Zone und SVWS-Zone. | ADR-001, ADR-007 | Docker-Compose-Netze, spaetere Firewall-Regeln, Netzdiagramm, Review der exponierten Ports. |
| Webanwendungen | Admin-SPA und Control-Plane-API verarbeiten privilegierte Verwaltungsfunktionen. | ADR-003, ADR-005, ADR-009 | Auth-/Rollen-Tests, Security-Header/CSP, RFC-7807-Fehlerformat, keine Tokens in `localStorage`. |
| Identitaets- und Berechtigungsmanagement | Keycloak als zentrale OIDC-Komponente fuer Admins und API-Clients. | ADR-005 | Realm-Export, Rollen-/Client-Konzept, 401/403-Tests, Token-Lebensdauer-Dokumentation. |
| Mandantentrennung | Schultraeger duerfen keine Daten anderer Mandanten sehen. | ADR-002, ADR-008, ADR-009 | RLS-Waechter-Test, Cross-Tenant-Negativtests, ArchUnit-Test fuer Operator-Zugriff. |
| Protokollierung und Nachvollziehbarkeit | Admin-Operationen und Gateway-Zugriffe muessen auditierbar sein. | ADR-004, ADR-009 | `audit_admin`-Tests, Gateway-Audit-Konzept, strukturierte Logs ohne Secrets oder Schuelerdaten. |
| Secret-Handling | SVWS-Zugangsdaten und Master-Key sind besonders kritisch. | ADR-006 | SecretStore-Tests, `.env.example` ohne echte Werte, Backup-/Recovery-Konzept fuer Master-Key. |
| Container-Betrieb | Deployment erfolgt als Container; lokale Entwicklung ueber Docker Compose. | ADR-003, ADR-007 | Compose-Healthchecks, minimal exponierte Ports, spaetere Image-/Patch-Policy. |
| Softwareentwicklung | Sicherheitsanforderungen sollen durch Tests und ADRs abgesichert werden. | alle ADRs | Maven-/Frontend-Tests, RLS-Waechter, ArchUnit, Pull-Request-Reviews, spaetere CI. |
| Datensicherung / Wiederherstellung | Verlust von PostgreSQL-Daten oder Master-Key gefaehrdet Betrieb und Verfuegbarkeit. | ADR-006, ADR-009 | Backup-Konzept fuer PostgreSQL, Key-Backup, Restore-Test, Audit-Aufbewahrung. |

## Offene Klaerungen

- Zielplattform des produktiven Betriebs: Docker Compose, Kubernetes oder RZ-spezifische Plattform.
- Konkrete BSI-Bausteinliste mit dem RZ-Betrieb/ISB abstimmen.
- Aufbewahrungsfristen und Verdichtung fuer Admin- und Gateway-Audit festlegen.
- Verfahren fuer Key-Rotation und Restore-Test ausarbeiten.
