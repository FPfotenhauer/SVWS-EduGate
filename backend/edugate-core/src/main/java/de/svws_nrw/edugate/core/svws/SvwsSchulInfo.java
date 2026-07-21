package de.svws_nrw.edugate.core.svws;

/**
 * Schulinformationen aus der SVWS-Privileged-API {@code GET /api/schema/liste/info/{schema}/schule}
 * ({@code SchuleInfo} in {@code examples/open-api-privileged.json}) - reine Stammdaten einer
 * Schule (Schulnummer, Schulform, Bezeichnung, Adresse), keine Zugangsdaten oder sonstigen
 * Secrets. Dient in ADR-014 Schritt 2 ausschließlich als unverbindliche Orientierung beim
 * Zuordnen eines SVWS-Schema-Funds zu einer Schule - EduGate leitet daraus niemals automatisch
 * einen Schulträger oder eine Schule ab.
 */
public record SvwsSchulInfo(
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
