package de.svws_nrw.edugate.core.svws;

/**
 * Ergebnis eines {@link SvwsConnectionTester#test(String, String, String)}-Aufrufs.
 *
 * <p>{@code message} ist immer ein kurzer, sicherer, deutschsprachiger Text für die
 * Admin-Oberfläche – niemals eine rohe Exception-Meldung, ein Stacktrace oder ein
 * Response-Body (ADR-006: keine internen Details, keine Secrets).
 */
public record SvwsConnectionTestResult(boolean success, String message) {

    public static SvwsConnectionTestResult success(final String message) {
        return new SvwsConnectionTestResult(true, message);
    }

    public static SvwsConnectionTestResult failure(final String message) {
        return new SvwsConnectionTestResult(false, message);
    }
}
