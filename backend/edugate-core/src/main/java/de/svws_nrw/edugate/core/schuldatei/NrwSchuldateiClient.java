package de.svws_nrw.edugate.core.schuldatei;

/**
 * Port für den Abruf der amtlichen NRW-Schuldatei (ADR-020 "Landes-Schuldatei als
 * Referenzkatalog"). Anders als {@code SvwsPrivilegedApiClient} zeigt dieser Port auf feste,
 * öffentliche Landes-Endpunkte ohne Zugangsdaten - kein SSRF-Risiko über Betreiber-Eingaben, da
 * das Ziel nicht konfigurierbar ist.
 *
 * <p>Implementierungen werfen für Netzwerk-/Protokoll-/Parsingfehler niemals eine Exception,
 * sondern liefern immer ein sicheres {@link NrwSchuldateiResult#failure(String)} (ADR-006).
 */
public interface NrwSchuldateiClient {

    /**
     * Ruft die aktuelle NRW-Schuldatei ab und liefert die daraus abgeleiteten Schul- und
     * Schulträger-Einträge (nur {@code oeart=1}/{@code oeart=2}, alle anderen
     * Organisationseinheiten-Arten werden verworfen, ADR-020-Nachtrag).
     */
    NrwSchuldateiResult fetchSchuldatei();
}
