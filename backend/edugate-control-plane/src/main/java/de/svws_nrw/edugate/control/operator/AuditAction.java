package de.svws_nrw.edugate.control.operator;

/**
 * Auditierte fachliche Aktionen (ADR-009). Die meisten laufen über {@link OperatorAccess}; die
 * als "gefährlich" eingestuften Schema-/Schule-Aktionen ({@code SCHEMA_*}, {@code SCHULE_*})
 * bilden eine bewusste, dokumentierte Ausnahme und werden stattdessen von den jeweiligen
 * tenant-gebundenen Services selbst geschrieben (siehe {@code SchemaService}/{@code SchuleService}).
 */
public enum AuditAction {
    SCHULTRAEGER_LIST,
    SCHULTRAEGER_CREATE,
    SCHULTRAEGER_READ,
    SCHULTRAEGER_UPDATE,
    SCHULTRAEGER_DEACTIVATE,
    SCHULTRAEGER_REACTIVATE,
    SVWS_INSTANZ_LIST,
    SVWS_INSTANZ_CREATE,
    SVWS_INSTANZ_READ,
    SVWS_INSTANZ_UPDATE,
    SVWS_INSTANZ_CREDENTIALS_SET,
    SVWS_INSTANZ_DEACTIVATE,
    SVWS_INSTANZ_CONNECTION_TEST,
    SCHEMA_CREATE,
    SCHEMA_UPDATE,
    SCHEMA_DEACTIVATE,
    SCHEMA_OVERVIEW,
    SCHULE_DEACTIVATE,
    SCHEMA_UMGEBUNG_LIST,
    SCHEMA_UMGEBUNG_CREATE,
    SCHEMA_UMGEBUNG_UPDATE,
    SCHEMA_UMGEBUNG_DEACTIVATE
}
