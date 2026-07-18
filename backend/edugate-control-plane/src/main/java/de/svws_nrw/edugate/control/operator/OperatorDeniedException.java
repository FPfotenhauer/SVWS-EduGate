package de.svws_nrw.edugate.control.operator;

/**
 * Innerhalb einer {@link OperatorOperation} zu werfen, wenn eine fachliche Autorisierungsregel
 * die Operation verweigert (z. B. eine zukünftige feingranulare Regel oberhalb von
 * {@code @RolesAllowed}). Führt zu Rollback der Fachtransaktion und einem
 * {@code audit_admin}-Eintrag mit {@link AuditOutcome#DENIED} in einer eigenen Transaktion
 * (ADR-009).
 */
public class OperatorDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperatorDeniedException(final String message) {
        super(message);
    }
}
