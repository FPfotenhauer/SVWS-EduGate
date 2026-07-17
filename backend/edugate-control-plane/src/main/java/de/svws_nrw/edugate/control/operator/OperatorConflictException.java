package de.svws_nrw.edugate.control.operator;

/**
 * Innerhalb einer {@link OperatorOperation} zu werfen, wenn eine Eindeutigkeitsbedingung
 * verletzt wird (z. B. doppelte Trägernummer). Führt zu Rollback der Fachtransaktion und
 * einem {@code audit_admin}-Eintrag mit {@link AuditOutcome#ERROR} in einer eigenen
 * Transaktion (ADR-009).
 */
public class OperatorConflictException extends RuntimeException {

    public OperatorConflictException(final String message) {
        super(message);
    }
}
