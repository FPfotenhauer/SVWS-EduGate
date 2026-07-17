package de.svws_nrw.edugate.control.operator;

/**
 * Innerhalb einer {@link OperatorOperation} zu werfen, wenn die angefragte Entität nicht
 * existiert. Führt zu Rollback der Fachtransaktion und einem {@code audit_admin}-Eintrag
 * mit {@link AuditOutcome#ERROR} in einer eigenen Transaktion (ADR-009).
 */
public class OperatorNotFoundException extends RuntimeException {

    public OperatorNotFoundException(final String message) {
        super(message);
    }
}
