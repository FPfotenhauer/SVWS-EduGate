package de.svws_nrw.edugate.core.schuldatei;

/**
 * Ein Schul-Datensatz aus der amtlichen NRW-Schuldatei
 * ({@code organisationseinheit} mit {@code oeart=1}, ADR-020-Nachtrag "Grundlagenrecherche vor
 * Umsetzung"). Rein referenzielle Stammdaten, kein EduGate-Betreiberbestand - siehe
 * {@code schule_katalog}.
 */
public record NrwSchuleEintrag(
    String schulnummer,
    String schulname,
    String schultraegernummer,
    String schulform,
    String strasse,
    String plz,
    String ort,
    String kreis,
    String telefon,
    String fax,
    String email,
    String homepage,
    String aufloesung
) {
}
