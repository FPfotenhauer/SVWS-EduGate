package de.svws_nrw.edugate.core.svws;

/**
 * Port für den Verbindungstest gegen eine SVWS-Instanz mit privilegierten Zugangsdaten.
 *
 * <p>Implementierungen werfen für Netzwerk-/Protokollfehler (Timeout, Verbindungsfehler,
 * unerwarteter Statuscode) niemals eine Exception, sondern liefern immer ein
 * {@link SvwsConnectionTestResult#failure(String)} mit einer sicheren, deutschsprachigen
 * Meldung ohne Secrets oder interne Details (ADR-006).
 *
 * <p>Dieser Port ist bewusst generisch gehalten (nur Base-URL und Zugangsdaten, keine
 * SVWS-Instanz-Referenz): Eine spätere Massenverwaltung (Batch-Verbindungstest über viele
 * Instanzen, CSV-/JSON-Import mit Preview vor dem Speichern, Wiederverwendung für
 * Schul-Schema-Zugangsdaten) soll auf demselben Port aufsetzen können, statt eigene
 * HTTP-Logik zu duplizieren. Das ist in diesem Auftrag bewusst nicht implementiert.
 */
public interface SvwsConnectionTester {

    /** Prüft Erreichbarkeit und Gültigkeit/Rechte der übergebenen Zugangsdaten in einem Schritt. */
    SvwsConnectionTestResult test(String baseUrl, String username, String password);

    /**
     * Prüft ausschließlich die reine Erreichbarkeit der SVWS-Instanz, ohne Zugangsdaten - für
     * den Fall, dass (noch) keine Zugangsdaten hinterlegt sind. Liefert bewusst keine Aussage
     * über Gültigkeit oder Rechte irgendwelcher Zugangsdaten.
     */
    SvwsConnectionTestResult testReachability(String baseUrl);
}
