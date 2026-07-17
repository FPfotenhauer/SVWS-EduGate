# Vorlaeufige Schutzbedarfsfeststellung

Diese Datei dokumentiert erste Schutzbedarfsannahmen fuer SVWS-EduGate. Sie ersetzt keine formale Schutzbedarfsfeststellung des Betreibers, dient aber als Arbeitsgrundlage fuer Architektur und Umsetzung.

## Schutzobjekte

| Schutzobjekt | Vertraulichkeit | Integritaet | Verfuegbarkeit | Begruendung |
|--------------|-----------------|-------------|----------------|------------|
| SVWS-Zugangsdaten | sehr hoch | hoch | hoch | Kompromittierung ermoeglicht Zugriff auf Schulverwaltungsdaten. |
| Mandantenmodell (`schultraeger`, `schule`, `svws_instanz`, `schema`) | hoch | sehr hoch | hoch | Falsche Zuordnung kann Mandantentrennung und Routing verletzen. |
| Admin-Audit (`audit_admin`) | hoch | sehr hoch | mittel | Nachweise duerfen nicht manipuliert oder unvollstaendig sein. |
| Gateway-Audit | hoch | sehr hoch | mittel | Nachvollziehbarkeit von Schuldatenzugriffen ist sicherheits- und datenschutzrelevant. |
| Admin-SPA und Control Plane | hoch | hoch | mittel | Privilegierte Verwaltungsfunktionen fuer Dienstleister-Admins. |
| Gateway | hoch | hoch | hoch | Zentraler Zugriffspfad fuer API-Clients auf SVWS-Daten. |
| Keycloak-Realm | hoch | sehr hoch | hoch | Rollen, Clients und Token-Ausstellung steuern den Zugriff. |

## Leitannahmen

- Schueler- und Schuldaten sind personenbezogene Daten und besonders schuetzenswert.
- EduGate greift auf Schuldaten ausschliesslich ueber die SVWS-API zu, nicht direkt auf die MariaDB der SVWS-Server.
- Mandantentrennung ist ein Kernschutzbedarf; Fehler duerfen nicht zu mandantenuebergreifender Datensicht fuehren.
- Secrets duerfen weder ins Frontend noch in Logs, API-Antworten oder Repository-Dateien gelangen.

## Konsequenzen fuer die Umsetzung

- Row-Level Security mit `FORCE` ist Pflicht fuer mandantenbezogene Tabellen.
- Operator-Zugriff ist zu kapseln und auditpflichtig.
- Fehler- und Negativtests sind Sicherheitsnachweise, nicht nur Qualitaetssicherung.
- Backup, Restore und Key-Rotation muessen vor produktiver Nutzung dokumentiert und getestet werden.
