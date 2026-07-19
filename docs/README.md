# SVWS-EduGate – Dokumentation

Diese Dokumentation richtet sich an alle, die mit SVWS-EduGate arbeiten, ohne zuerst die
vollständige Architekturdokumentation oder alle Architecture Decision Records (ADRs) lesen zu
müssen: Betreiber, Administratoren, Anwender, Sicherheits-/Compliance-Verantwortliche und
Entwicklerinnen und Entwickler.

## Was ist SVWS-EduGate?

SVWS-EduGate ist ein Werkzeug für Dienstleister, die SVWS-Server für mehrere Schulträger in einem
BSI-zertifizierten Rechenzentrum betreiben. Es besteht aus zwei Teilen:

- **Control Plane** – Verwaltung der Mandantenhierarchie (Schulträger → Schule → Schema) und der
  SVWS-Instanzen durch den Dienstleister.
- **Data Plane / Gateway** – gesicherter, mandantengetrennter Zugriff auf Schuldaten über die
  SVWS-Server-API, zunächst nur im internen Netz.

Details und Hintergründe: [`architecture/ARCHITECTURE.md`](../architecture/ARCHITECTURE.md).

## Für wen ist diese Dokumentation gedacht?

| Bereich | Zielgruppe |
|---|---|
| [`betreiber/`](./betreiber/README.md) | Rechenzentrum, Dienstleisterbetrieb, technischer Betrieb |
| [`administration/`](./administration/README.md) | Dienstleister-Admins |
| [`anwender/`](./anwender/README.md) | Spätere fachliche/technische Nutzer der freigegebenen Funktionen |
| [`sicherheit-compliance/`](./sicherheit-compliance/README.md) | ISB, Datenschutz, Audit, Projektverantwortliche |
| [`entwicklung/`](./entwicklung/README.md) | Entwicklerinnen, Entwickler, Coding-Agenten |

## Wo liegen Architekturentscheidungen?

Alle verbindlichen Architekturentscheidungen stehen als ADRs unter
[`architecture/adr/`](../architecture/adr/README.md), die Gesamtsicht in
[`architecture/ARCHITECTURE.md`](../architecture/ARCHITECTURE.md) (arc42-Struktur). Diese
`docs/`-Struktur ersetzt das nicht, sondern führt zielgruppengerecht dorthin.

## Wo liegen Compliance-/Nachweisdokumente?

Die projektbezogene Sicherheits- und Compliance-Dokumentation (BSI-Grundschutz-Mapping,
Schutzbedarfsfeststellung, Risikoannahmen, Nachweis-Checkliste) liegt unter
[`compliance/`](../compliance/README.md). Der Bereich
[`sicherheit-compliance/`](./sicherheit-compliance/README.md) in diesem `docs/`-Ordner ordnet
diese Dokumente kurz ein, ersetzt sie aber nicht.

## Abgrenzung der drei Ordner

| Ordner | Zweck |
|---|---|
| `architecture/` | Verbindliche Architekturentscheidungen und Gesamtarchitektur (arc42, ADRs). |
| `compliance/` | Projektbezogene Sicherheits-/Compliance-Nachweise (BSI-Grundschutz, Schutzbedarf, Risiken). |
| `docs/` | Nutzerorientierte Einstiegspunkte für Betrieb, Administration, Anwendung, Sicherheit/Compliance und Entwicklung – verlinkt in die beiden anderen Ordner, statt sie zu duplizieren. |

## Aktueller Stand

Der aktuelle Implementierungsstand umfasst eine nutzbare Admin-SPA und Control-Plane-API für:

- Schulträger,
- Schulen und Ansprechpartner,
- SVWS-Instanzen inklusive verschlüsselter Zugangsdaten und Verbindungstest,
- Schuldatenbanken/Schemata je Schule und Umgebung,
- eine mandantenübergreifende Schuldatenbank-Übersicht,
- betreiberseitig verwaltbare Schema-Umgebungen.

Die Daten werden mit PostgreSQL Row-Level Security und auditiertem Admin-Zugriff geschützt. Der
Gateway-Service ist weiterhin nur als OIDC-/Health-/`/ping`-Skeleton vorhanden; Proxy-Logik und
echte Gateway-Mandantenauflösung folgen später.

Wichtig: Schuldatenbanken werden aktuell in EduGate geplant und verwaltet. Echte
SVWS-Privileged-API-Operationen wie Schema-Anlage, Migration, Import, Export oder Löschen sind noch
nicht verdrahtet und werden nach ADR-014 als eigene, geschützte Workflows umgesetzt.
