# Compliance-Dokumentation

Dieser Ordner sammelt die projektbezogene Sicherheits- und Compliance-Dokumentation fuer SVWS-EduGate. Ziel ist kein Ersatz fuer ein vollstaendiges IT-Grundschutz-Kompendium, sondern eine nachvollziehbare Bruecke zwischen Architekturentscheidungen, Umsetzung und spaeteren Nachweisen.

## Dokumente

| Dokument | Zweck |
|----------|-------|
| [`bsi-grundschutz-mapping.md`](./bsi-grundschutz-mapping.md) | Erste Zuordnung relevanter BSI-Grundschutz-Bausteine zu Architekturentscheidungen und technischen Nachweisen. |
| [`schutzbedarfsfeststellung.md`](./schutzbedarfsfeststellung.md) | Vorlaeufige Schutzbedarfsannahmen fuer Daten, Komponenten und Schnittstellen. |
| [`risikoannahmen.md`](./risikoannahmen.md) | Projektbezogene Risiken, Annahmen und geplante Gegenmassnahmen. |
| [`nachweis-checkliste.md`](./nachweis-checkliste.md) | Pruefbare Nachweise, die waehrend Implementierung und Betrieb entstehen sollen. |

## Arbeitsweise

- Compliance-Anforderungen werden moeglichst auf konkrete ADRs, Tests, Konfigurationen oder Betriebsnachweise abgebildet.
- Neue sicherheitsrelevante Architekturentscheidungen sollen hier verlinkt werden, statt unverbunden in Einzeldokumenten zu bleiben.
- Nachweise werden erst als `geplant` markiert und spaeter mit konkreten Artefakten ergaenzt.
- Es werden keine Secrets, Zugangsdaten, internen Netzdetails oder produktiven Konfigurationswerte dokumentiert.
