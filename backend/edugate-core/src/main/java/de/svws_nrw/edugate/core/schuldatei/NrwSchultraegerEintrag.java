package de.svws_nrw.edugate.core.schuldatei;

/**
 * Ein Schulträger-Datensatz aus der amtlichen NRW-Schuldatei
 * ({@code organisationseinheit} mit {@code oeart=2}, ADR-020-Nachtrag "Grundlagenrecherche vor
 * Umsetzung"). Schulträger sind in der Quelle vollwertige, eigenständige Organisationseinheiten
 * mit eigener Adresse - kein bloßes Unterfeld eines Schul-Datensatzes. Rein referenzielle
 * Stammdaten, kein EduGate-Betreiberbestand - siehe {@code schultraeger_katalog}.
 */
public record NrwSchultraegerEintrag(
    String traegernummer,
    String traegername,
    String traegerschaftsart,
    String strasse,
    String plz,
    String ort,
    String aufloesung
) {
}
