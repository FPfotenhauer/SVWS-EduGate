# ADR-022: Interne PKI- und Truststore-Strategie fuer SVWS-TLS

- **Status:** proposed
- **Datum:** 2026-07-22
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate verbindet sich aus der Control Plane zu SVWS-Instanzen ueber HTTPS. In
Rechenzentrumsumgebungen ist es haeufig, dass keine oeffentlichen CA-Zertifikate genutzt werden,
sondern interne Zertifizierungsstellen (PKI) oder selbstsignierte Zertifikate.

Im Projekt existiert bereits ein optionaler zusaetzlicher Truststore fuer lokale Entwicklung
(`EDUGATE_SVWS_DEV_TRUSTSTORE_PATH`, `EDUGATE_SVWS_DEV_TRUSTSTORE_PASSWORD`,
`DevTrustStoreSslContext`). Dieser Mechanismus erweitert die JVM-Vertrauenskette additiv und ist
fuer lokale Testinstanzen gedacht.

Offen war die betriebliche Leitlinie fuer produktionsnahe RZ-Setups: Wie soll EduGate mit intern
signierten Zertifikaten von SVWS-Instanzen umgehen, ohne Sicherheitsziele aus ADR-007/ADR-016 zu
unterlaufen?

## Betrachtete Optionen

1. **Nur oeffentliche CAs erlauben:** Einfaches Betriebsmodell, aber unrealistisch fuer viele
   abgeschottete RZ-Umgebungen.
2. **Beliebige selbstsignierte Leaf-Zertifikate pro Instanz manuell importieren:** technisch
   moeglich, aber hoher Betriebsaufwand, schwierige Rotation und erhoehte Fehleranfaelligkeit.
3. **Interne PKI (Root/Intermediate CA) als Standard, verwaltet ueber zentralen Truststore:**
   passt zu RZ-Betrieb, skaliert besser und erlaubt kontrollierte Rotation.

## Entscheidung

Gewaehlt wird **Option 3**.

Fuer produktionsnahe und produktive Deployments gilt:

- SVWS-Zertifikate sollen durch eine interne PKI (oder andere organisatorisch gleichwertige
  Vertrauenskette) signiert sein.
- EduGate vertraut dieser Kette ueber einen kontrollierten Truststore-Prozess.
- Einfache, lose verteilte selbstsignierte Leaf-Zertifikate sind nur fuer lokale Entwicklung und
  kurzfristige Testfaelle vorgesehen, nicht als dauerhaftes Betriebsmodell.
- Es bleibt verboten, Zertifikatspruefung global zu deaktivieren oder pauschal allen Zertifikaten
  zu vertrauen.

## Konsequenzen

### Positiv

- Realistisches Modell fuer RZ-Betrieb ohne Abhaengigkeit von oeffentlichen CAs.
- Bessere Skalierbarkeit bei vielen SVWS-Instanzen durch zentrale Vertrauenskette.
- Klarere Betriebsprozesse fuer Zertifikatsrotation und Incident-Behandlung.
- Sicherheitsziele (Authentizitaet der Gegenstelle, kein Trust-All) bleiben erhalten.

### Negativ / Risiken

- Erfordert organisatorische PKI-Prozesse (Ausstellung, Verteilung, Widerruf, Rotation).
- Falsch gepflegte Truststores koennen zu Ausfaellen von Verbindungstests fuehren.
- Unterschiedliche Browser-/System-Truststores bleiben ein separates Thema fuer Admin-Zugriffe und
  sind nicht automatisch durch EduGate-Backend-Truststores abgedeckt.

## Betriebsleitplanken

- Dev-Truststore-Mechanismus dient primaer lokaler Entwicklung.
- Fuer produktive Umgebungen ist ein definierter Betreiberprozess fuer Truststore-Pflege
  verpflichtend (Versionierung, Rollout, Rollback, Monitoring).
- Zertifikate muessen zur genutzten Zieladresse passen (SAN-Validierung bleibt aktiv).

## Verweise

- ADR-007: Netzzonenkonzept und externe Ausbaustufe
- ADR-016: Deployment- und Auslieferungsmodell
- `backend/edugate-core/src/main/java/de/svws_nrw/edugate/core/svws/DevTrustStoreSslContext.java`
- `docs/entwicklung/svws-server-api.md`
- `.env.example`