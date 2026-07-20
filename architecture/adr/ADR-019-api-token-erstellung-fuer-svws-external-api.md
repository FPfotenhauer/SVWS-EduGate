# ADR-019: API-Token-Erstellung für die SVWS-External-API

- **Status:** proposed
- **Datum:** 2026-07-20
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

SVWS-EduGate soll perspektivisch schulbezogene Zugänge zur SVWS-External-API verwalten können.
ADR-018 beschreibt bereits, dass heutige BasicAuth-Zugangsdaten später durch API-Token abgelöst
oder ergänzt werden sollen. Für Betreiber mit vielen Schulen darf die Anlage solcher Zugänge nicht
manuell pro Schule erfolgen. Bei mehreren hundert Schulen wäre eine händische Token-Erzeugung
betrieblicher Ballast und fehleranfällig.

Gleichzeitig sind API-Token produktive Secrets. Ein kompromittierter Token darf nicht zu einem
vollständigen administrativen Zugriff auf den SVWS-Server führen. Die hohe Berechtigung liegt beim
erzeugenden Benutzer oder Prozess, nicht beim erzeugten Token. Der erzeugte Token muss deshalb
schulbezogen, zweckgebunden, zeitlich begrenzt, widerrufbar und auditierbar sein.

Die Erzeugung dieser Token berührt zwei Systemgrenzen:

- SVWS-EduGate orchestriert Betreiberprozesse und verwaltet Metadaten, Berechtigungen, Audit und
  Exporte.
- Der SVWS-Server stellt die echte SVWS-Privileged-API und die SVWS-External-API bereit. Die
  eigentliche Token-Gültigkeit muss dort durchgesetzt werden, wo die External-API aufgerufen wird.

ADR-014 legt fest, dass echte Aufrufe der SVWS-Privileged-API in EduGate nur über geschützte
Betreiber-Workflows erfolgen. API-Token-Erzeugung ist ein solcher geschützter Workflow.

## Betrachtete Optionen

1. **Manuelle Token-Erzeugung im SVWS-Server je Schule:** einfach zu verstehen, aber für große
   Betreiberumgebungen nicht praktikabel. Der Prozess wäre langsam, uneinheitlich und schwer
   auditierbar.
2. **Ein gemeinsamer Token für viele oder alle Schulen:** operativ bequem, aber sicherheitlich
   ungeeignet. Ein Leak beträfe mehrere Mandanten und ließe sich nicht schulweise rotieren oder
   sperren.
3. **EduGate erzeugt und speichert eigene Tokens ohne SVWS-Server-Beteiligung:** technisch im
   Gateway möglich, aber fachlich falsch, wenn der Zugriff auf die echte SVWS-External-API im
   SVWS-Server autorisiert werden muss. EduGate würde damit ein paralleles Autorisierungssystem
   neben dem SVWS-Server etablieren.
4. **EduGate stößt die Token-Erzeugung über die SVWS-Privileged-API an:** EduGate führt den
   Betreiberworkflow, der SVWS-Server erzeugt oder registriert die Token und erzwingt deren
   Gültigkeit an der External-API. Das ist automatisierbar und hält die Autorisierung am richtigen
   System.

## Entscheidung

Vorläufig gewählt wird **Option 4**.

SVWS-EduGate erhält einen geschützten Betreiberworkflow, der schulbezogene API-Token für die
SVWS-External-API über die SVWS-Privileged-API erzeugt. Für SVWS-EduGate wird ein eigener
Token-Zweck vorgesehen, zum Beispiel `SVWS_EDUGATE`. Tokens mit diesem Zweck erhalten nur die für
SVWS-EduGate notwendigen Rechte auf der External-API.

### Grundregeln

- Pro Schule wird ein eigener Token erzeugt.
- Ein Token ist an genau den fachlichen Zweck und den schulischen Kontext gebunden, für den er
  erstellt wurde.
- Der Token ist zeitlich begrenzt und widerrufbar.
- Der Token-Klartext wird nur einmalig bei der Erzeugung angezeigt oder exportiert.
- Der SVWS-Server speichert Token nicht im Klartext, sondern nur als Hash oder bevorzugt als HMAC.
- EduGate speichert oder exportiert Token-Klartexte nur nach den Regeln aus ADR-006 und ADR-018.
- Erzeugung, Widerruf und Export sind auditpflichtige Betreiberoperationen.
- Token dürfen nicht in Logs, Fehlermeldungen, Auditdetails oder UI-Bestätigungen erscheinen.

### Token-Format

Ein Token besteht aus einem nicht geheimen Präfix und einem kryptografisch zufälligen geheimen
Anteil.

Beispiel:

```text
svws-eg-v1_<zufälliger-token-anteil>
```

Das Präfix dient der Erkennung des Token-Typs, der Version und des Einsatzzwecks im Betrieb. Es
darf nicht für die Sicherheitsbewertung herangezogen werden. Die Sicherheit des Tokens beruht
ausschließlich auf dem geheimen Anteil.

Der geheime Anteil wird mit einem kryptografisch sicheren Zufallszahlengenerator erzeugt. Die
Mindeststärke beträgt 256 Bit Zufall, zum Beispiel 32 zufällige Bytes, Base64URL-kodiert ohne
Padding.

Tokens dürfen nicht aus Schulnummern, Zeitstempeln, UUIDs allein, fortlaufenden Zählern oder
Hashes vorhersagbarer Daten abgeleitet werden.

### Speicherung und Prüfung im SVWS-Server

Der SVWS-Server speichert API-Token nicht im Klartext. Gespeichert wird nur ein Hash oder bevorzugt
ein HMAC des vollständigen Tokens.

Empfohlen:

```text
token_hash = HMAC-SHA-256(serverseitiges-secret, token)
```

Alternativ ist ein SHA-256-Hash des Tokens möglich, sofern der Token ausreichend zufällig ist. Ein
HMAC mit serverseitigem Secret reduziert das Risiko bei einem reinen Datenbankabfluss zusätzlich.

Bei jedem Aufruf der SVWS-External-API mit API-Token werden mindestens folgende Prüfungen
durchgeführt:

1. Token syntaktisch gültig.
2. Hash oder HMAC des Tokens ist bekannt.
3. Token ist nicht widerrufen.
4. Token ist nicht abgelaufen.
5. Token-Zweck passt zur verwendeten API.
6. Token-Scopes erlauben den konkreten Zugriff.
7. Schulbindung des Tokens passt zum angefragten Kontext.

Fehlschläge werden ohne Ausgabe sensibler Details beantwortet.

### Fachliches Datenmodell

Für die Token-Verwaltung werden mindestens folgende fachliche Informationen benötigt:

```text
id
schule_id oder schulnummer
svws_instanz_id
purpose
display_name
token_hash oder token_reference
scopes
created_by_user_id
created_at
expires_at
last_used_at
revoked_at
revoked_by_user_id
revoked_reason
```

Ob `token_hash` direkt in EduGate bekannt ist oder EduGate nur eine Referenz auf einen im
SVWS-Server verwalteten Token hält, ist eine Implementierungsentscheidung entlang der finalen
SVWS-Server-API. Entscheidend ist, dass EduGate den Betreiberworkflow, den Status, die
Schulzuordnung, Ablaufdaten und Auditinformationen nachvollziehen kann, ohne Token-Klartexte
dauerhaft zu benötigen.

### Zeitliche Begrenzung und Rotation

API-Token werden zeitlich begrenzt. Für SVWS-EduGate wird eine reguläre Gültigkeit von 12 Monaten
empfohlen.

Empfohlene Regeln:

- Standardgültigkeit: 12 Monate
- maximale Gültigkeit: 18 Monate
- minimale Gültigkeit: 30 Tage
- Warnhinweis ab 60 Tage vor Ablauf
- dringender Warnhinweis ab 14 Tage vor Ablauf
- optionale Grace-Phase: 14 bis 30 Tage, sofern betrieblich notwendig

Eine Rotation muss überlappend möglich sein:

1. Der bisherige Token ist aktiv.
2. Ein neuer Token wird erzeugt.
3. SVWS-EduGate oder der angebundene Betreiberprozess stellt auf den neuen Token um.
4. Der SVWS-Server erkennt die Nutzung des neuen Tokens über `last_used_at`.
5. Der bisherige Token wird widerrufen oder läuft nach einer Übergangszeit aus.

Um Token-Wildwuchs zu vermeiden, sollte pro Schule und Zweck nur eine begrenzte Anzahl aktiver
Token erlaubt sein. Für die Rotation sind maximal zwei aktive Token pro Schule und Zweck
ausreichend.

### Betreiberworkflow in EduGate

EduGate stellt für Betreiber einen geschützten Workflow bereit, der einzelne oder mehrere Token
erzeugen kann. Ein Bulk-Prozess für viele Schulen ist ausdrücklich vorgesehen.

Der Workflow muss mindestens anzeigen oder festlegen:

- Schulträger
- Schule
- SVWS-Instanz
- Schema oder Schuldatenbankkontext
- Token-Zweck
- Scopes
- Ablaufdatum
- Erzeugungs- und Exportstatus

Für Bulk-Erzeugung gilt:

- Der Prozess ist nur mit passender Betreiberberechtigung erlaubt.
- Die Anzahl der betroffenen Schulen wird vor Ausführung klar angezeigt.
- Die Ausgabe mit Token-Klartext ist ein hochsensibles Artefakt.
- Token-Klartexte werden nicht in EduGate-Logs oder Auditdetails geschrieben.
- Der Export sollte in das Format aus ADR-018 eingebettet werden können.

## Konsequenzen

### Positiv

- Betreiber können API-Zugänge für viele Schulen automatisiert erzeugen.
- Ein Token-Leak bleibt auf Schule, Zweck und Laufzeit begrenzt.
- Token können schulweise rotiert oder widerrufen werden.
- EduGate bleibt Orchestrierungssystem, während der SVWS-Server die External-API-Autorisierung
  durchsetzt.
- Das Modell passt zu ADR-018 und kann in Secret-/Vault-Prozesse eingebunden werden.
- Ablaufdaten und `last_used_at` ermöglichen proaktive Rotation statt Ausfälle durch unerwartet
  abgelaufene Token.

### Negativ / Risiken

- Der SVWS-Server benötigt ein eigenes Token-Modell und Prüfungen an der External-API.
- Die SVWS-Privileged-API muss Token-Erzeugung, Widerruf und ggf. Statusabfragen bereitstellen.
- EduGate benötigt zusätzliche Betreiber-Workflows, Berechtigungen und Audit-Einträge.
- Bulk-Erzeugung produziert kurzfristig viele Klartext-Secrets und muss besonders sorgfältig
  abgesichert werden.
- Zu kurze Laufzeiten erzeugen Betriebsaufwand; zu lange Laufzeiten erhöhen das Risiko bei
  unbemerktem Leak.

## Verweise

- ADR-006 (Secret-Handling für SVWS-Zugangsdaten)
- ADR-009 (Operator-Zugriff und Audit-Modell für Admin-Operationen)
- ADR-014 (Verwendung echter Aufrufe der SVWS-Privileged-API)
- ADR-015 (Rollen- und Rechte-Management für Betreiber und API-Gateway)
- ADR-018 (Exportformat für Verbindungsdaten von Schuldatenbanken)
