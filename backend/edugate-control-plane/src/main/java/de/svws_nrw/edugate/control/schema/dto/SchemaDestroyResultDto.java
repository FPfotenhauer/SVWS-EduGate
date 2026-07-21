package de.svws_nrw.edugate.control.schema.dto;

/**
 * Ergebnis eines Löschversuchs eines echten Schemas über die SVWS-Privileged-API
 * (ADR-012/ADR-014). Ein fachlicher Fehlschlag (fehlende Zugangsdaten, SVWS-seitige Ablehnung,
 * Netzwerkfehler) ist kein Serverfehler - der Versuch selbst gilt als durchgeführt, siehe
 * {@code SchemaService#destroy}.
 */
public record SchemaDestroyResultDto(boolean success, String message) {
}
