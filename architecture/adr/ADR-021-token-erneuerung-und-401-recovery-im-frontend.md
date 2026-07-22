# ADR-021: Token-Erneuerung und 401-Recovery im Frontend

- **Status:** accepted
- **Datum:** 2026-07-22
- **Entscheider:** Franko Pfotenhauer

## Kontext und Problemstellung

In der Webanwendung traten nach Inaktivitaet wiederholt `401 Unauthorized`-Fehler auf, z. B. beim
Aufruf von `/admin/api/v1/schultraeger?page=0&size=100`.

Die Analyse ergab:

- Das Keycloak-Realm setzt `accessTokenLifespan` auf 300 Sekunden (5 Minuten).
- Das Frontend arbeitete mit `oidc-client-ts` im In-Memory-Store ohne aktive Silent-Renew-Strategie.
- API-Requests nutzten das jeweils vorhandene Bearer-Token direkt und brachen bei `401` sofort ab.
- Es gab keinen zentralen Recovery-Pfad (Token erneuern und Request einmal wiederholen).

Effekt: Nach Ablauf des Access-Tokens schlugen API-Requests fehl, bis ein Browser-Refresh den
Anmeldefluss erneut anstiess und damit ein frisches Token beschaffte.

## Betrachtete Optionen

1. **Nur Token-Lebensdauer im Realm erhoehen:** reduziert die Haeufigkeit, behebt aber die Ursache
   nicht (fehlender Frontend-Recovery-Pfad).
2. **Bei jedem `401` sofort hart auf Login umleiten:** robust, aber schlechtere UX und vermeidbare
   Unterbrechungen auch bei kurzer Inaktivitaet.
3. **Zentralen `401`-Recovery-Pfad im Frontend einfuehren:** bei `401` zuerst Token still erneuern,
   dann Request genau einmal wiederholen; nur bei Misserfolg auf Login umleiten.

## Entscheidung

Gewaehlt wurde **Option 3**.

Das Frontend fuehrt einen zentralen Recovery-Mechanismus fuer `401` ein:

- OIDC-Silent-Renew ist aktiviert.
- Ein zentraler Handler versucht bei `401` zuerst eine Token-Erneuerung.
- Ist die Erneuerung erfolgreich, wird der fehlgeschlagene API-Request genau einmal wiederholt.
- Ist die Erneuerung nicht moeglich (z. B. Session beendet), wird kontrolliert in den Login-Flow
  umgeleitet.
- Parallele Erneuerungsversuche werden dedupliziert (in-flight guard), um Lastspitzen und
  inkonsistente Zustaende zu vermeiden.

## Konsequenzen

### Positiv

- Deutlich weniger sichtbare `401`-Fehler nach Inaktivitaet.
- Nutzer muessen bei kurzzeitig abgelaufenem Access-Token typischerweise keinen manuellen
  Browser-Refresh mehr ausfuehren.
- Auth-Fehlerbehandlung ist zentralisiert statt in vielen Stores/View-Flows verteilt.
- Einmaliger Retry begrenzt Seiteneffekte und verhindert Endlosschleifen.

### Negativ / Risiken

- Etwas komplexere Frontend-Auth-Logik (Renew + Retry + Fallback).
- Silent-Renew haengt von einer gueltigen OIDC-Session im Identity Provider ab.
- Bei vollstaendig abgelaufener IdP-Session bleibt ein erneuter Login weiterhin erforderlich
  (beabsichtigtes Sicherheitsverhalten).

## Verweise

- ADR-005: Keycloak (OIDC) und Rollen-/Scope-Modell
- `docker/keycloak/realm-edugate.json` (`accessTokenLifespan`)
- `frontend/src/auth/oidcConfig.ts`
- `frontend/src/auth/authStore.ts`
- `frontend/src/api/httpClient.ts`
- `frontend/src/main.ts`