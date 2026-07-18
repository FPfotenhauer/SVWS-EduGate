package de.svws_nrw.edugate.control.operator;

/**
 * Innerhalb einer {@link OperatorOperation} zu werfen, wenn eine fachliche Vorbedingung des
 * aktuellen Zustands nicht erfüllt ist (z. B. Verbindungstest einer SVWS-Instanz ohne
 * hinterlegte Zugangsdaten). Führt zu Rollback der Fachtransaktion und einem
 * {@code audit_admin}-Eintrag mit {@link AuditOutcome#ERROR} in einer eigenen Transaktion
 * (ADR-009).
 */
public class OperatorPreconditionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperatorPreconditionException(final String message) {
        super(message);
    }
}
