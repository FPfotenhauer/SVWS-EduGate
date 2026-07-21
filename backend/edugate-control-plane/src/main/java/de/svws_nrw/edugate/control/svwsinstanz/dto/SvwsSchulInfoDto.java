package de.svws_nrw.edugate.control.svwsinstanz.dto;

/**
 * Schulstammdaten aus der SVWS-Privileged-API, gespiegelt aus {@code SvwsSchulInfo} (ADR-014
 * Schritt 2). Rein informativ im Zuordnungs-Modal - niemals Grundlage einer automatischen
 * Tenant-/Schulzuordnung.
 */
public record SvwsSchulInfoDto(
    Long schulnummer,
    String schulform,
    String bezeichnung,
    String strassenname,
    String hausnummer,
    String hausnummerZusatz,
    String plz,
    String ort
) {
}
