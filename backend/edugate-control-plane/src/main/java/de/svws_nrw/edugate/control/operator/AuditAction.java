package de.svws_nrw.edugate.control.operator;

/** Fachliche Aktionen, die über {@link OperatorAccess} auditiert werden (ADR-009). */
public enum AuditAction {
    SCHULTRAEGER_LIST,
    SCHULTRAEGER_CREATE,
    SCHULTRAEGER_READ,
    SCHULTRAEGER_UPDATE,
    SCHULTRAEGER_DEACTIVATE
}
