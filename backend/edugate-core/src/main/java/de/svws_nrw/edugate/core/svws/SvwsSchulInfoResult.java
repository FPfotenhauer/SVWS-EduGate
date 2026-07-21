package de.svws_nrw.edugate.core.svws;

/**
 * Ergebnis eines {@link SvwsPrivilegedApiClient#getSchulInfo(String, String, String, String)}-
 * Aufrufs. {@code message} ist immer ein kurzer, sicherer, deutschsprachiger Text (ADR-006),
 * analog zu {@link SvwsSchemaListResult}. Ein Fehlschlag (z. B. Schema ohne Schul-Informationen,
 * fehlende Rechte, Netzwerkfehler) ist fachlich erwartbar und darf laut ADR-014 die manuelle
 * Zuordnung eines Schema-Funds nicht blockieren - {@code schulInfo} ist dann {@code null}.
 */
public record SvwsSchulInfoResult(boolean success, String message, SvwsSchulInfo schulInfo) {

    public static SvwsSchulInfoResult success(final SvwsSchulInfo schulInfo) {
        return new SvwsSchulInfoResult(true, "Schul-Informationen erfolgreich abgerufen.", schulInfo);
    }

    public static SvwsSchulInfoResult failure(final String message) {
        return new SvwsSchulInfoResult(false, message, null);
    }
}
