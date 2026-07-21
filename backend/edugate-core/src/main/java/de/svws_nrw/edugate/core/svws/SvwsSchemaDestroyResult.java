package de.svws_nrw.edugate.core.svws;

/**
 * Ergebnis eines {@link SvwsPrivilegedApiClient#destroySchema(String, String, String, String)}-Aufrufs.
 *
 * <p>{@code message} ist immer ein kurzer, sicherer, deutschsprachiger Text (ADR-006: keine
 * rohen Exception-Meldungen, keine Secrets), analog zu {@link SvwsSchemaListResult}.
 */
public record SvwsSchemaDestroyResult(boolean success, String message) {

    public static SvwsSchemaDestroyResult erfolgreich() {
        return new SvwsSchemaDestroyResult(true, "Schema erfolgreich auf der SVWS-Instanz gelöscht.");
    }

    public static SvwsSchemaDestroyResult failure(final String message) {
        return new SvwsSchemaDestroyResult(false, message);
    }
}
