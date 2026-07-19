package de.svws_nrw.edugate.control.schema.dto;

/**
 * Ergebnis der Namensvorschlag-Abfrage (ADR-012). {@code schemaName} ist {@code null}, wenn die
 * Schule keine belastbare Schulnummer hat ({@code belastbareSchulnummer = false}) - die
 * Oberfläche muss dann einen expliziten technischen Schlüssel verlangen statt selbst zu raten.
 */
public record SchemaNamingSuggestionDto(String schemaName, boolean belastbareSchulnummer) {
}
