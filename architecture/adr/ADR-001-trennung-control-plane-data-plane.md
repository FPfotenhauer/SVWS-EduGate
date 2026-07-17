# ADR-001: Trennung von Control Plane und Data Plane

- **Status:** accepted
- **Datum:** 2026-07-16
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate verfolgt zwei Produktziele mit sehr unterschiedlichen Profilen:

1. **Serververwaltung** (Schulträger, Schulen, Instanzen, Schemas): wenige Nutzer, schreibende Zugriffe auf das Mandantenmodell, privilegierte SVWS-API-Aufrufe, ausschließlich internes Verwaltungsnetz.
2. **API-Gateway**: potenziell viele maschinelle Clients, hoher Lesedurchsatz, strikt begrenzte Scopes, perspektivisch extern exponiert.

Ein monolithischer Service müsste beide Profile gleichzeitig erfüllen; insbesondere würde eine spätere externe Exponierung des Gateways zwangsläufig auch Verwaltungscode und privilegierte Funktionen in die exponierte Angriffsfläche heben.

## Betrachtete Optionen

1. **Ein Service für alles** (wie SVWS-Main-Server): einfachster Start, aber Verwaltung und Gateway teilen Prozess, Deployment und Angriffsfläche.
2. **Zwei Services im Monorepo** (Control Plane + Gateway): getrennte Prozesse, getrennte Deployments und Netzzonen, gemeinsame Domänen-Bibliothek.
3. **Zwei getrennte Repositories:** maximale Trennung, aber hoher Abstimmungsaufwand für ein gemeinsames Mandantenmodell und ein kleines Team.

## Entscheidung

Gewählt wurde **Option 2**: Control Plane und Gateway sind eigenständige Quarkus-Services im selben Repository (Maven-Multi-Module: `edugate-core`, `edugate-control-plane`, `edugate-gateway`), mit eigenen Container-Images und eigener Netzplatzierung (siehe ADR-007).

Der Gateway-Service erhält ausschließlich **lesenden** Zugriff auf das Mandantenmodell (eigener PostgreSQL-User mit minimalen Rechten). Schreibzugriffe auf Mandantendaten sind der Control Plane vorbehalten.

## Konsequenzen

### Positiv

- Externe Exponierung betrifft später nur das Gateway; die Verwaltung verlässt nie das interne Netz.
- Unabhängige Skalierung (Gateway horizontal, Control Plane bleibt klein).
- Kleinere Angriffsfläche pro Service; klare Least-Privilege-Zuordnung bis auf DB-Ebene.
- BSI-Netzsegmentierung (NET.1.1) lässt sich direkt abbilden.

### Negativ / Risiken

- Zwei Deployments statt einem; etwas mehr Infrastruktur in Docker Compose.
- Gemeinsames Domänenmodell muss als Modul sauber geschnitten werden, um zyklische Abhängigkeiten zu vermeiden.

## Verweise

- ADR-004 (Gateway-Eigenbau), ADR-007 (Netzzonen)
