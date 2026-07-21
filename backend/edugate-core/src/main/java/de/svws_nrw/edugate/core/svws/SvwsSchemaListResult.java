package de.svws_nrw.edugate.core.svws;

import java.util.List;

/**
 * Ergebnis eines {@link SvwsPrivilegedApiClient#listSchemas(String, String, String)}-Aufrufs.
 *
 * <p>{@code message} ist immer ein kurzer, sicherer, deutschsprachiger Text (ADR-006: keine
 * rohen Exception-Meldungen, keine Secrets), analog zu {@link SvwsConnectionTestResult}.
 * {@code entries} ist bei einem Fehlschlag immer leer statt {@code null}.
 */
public record SvwsSchemaListResult(boolean success, String message, List<SvwsSchemaListeEintrag> entries) {

    public static SvwsSchemaListResult success(final List<SvwsSchemaListeEintrag> entries) {
        return new SvwsSchemaListResult(true, "Schema-Liste erfolgreich abgerufen.", List.copyOf(entries));
    }

    public static SvwsSchemaListResult failure(final String message) {
        return new SvwsSchemaListResult(false, message, List.of());
    }
}
