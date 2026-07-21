package de.svws_nrw.edugate.control.svwsinstanz.dto;

import java.time.Instant;

/**
 * Ergebnis eines Sync-Laufs gegen die SVWS-Privileged-API (ADR-014 Stufe 1). {@code success} und
 * {@code message} spiegeln {@code SvwsSchemaListResult} 1:1, damit ein Fehlschlag (z. B. fehlende
 * Zugangsdaten, ungültige Rechte, Netzwerkfehler) in der UI sichtbar wird, ohne dass der
 * REST-Aufruf selbst fehlschlägt (der Sync-Versuch selbst ist immer erfolgreich durchgeführt und
 * auditiert - nur das fachliche Ergebnis kann negativ sein).
 */
public record SvwsSchemaSyncResultDto(boolean success, String message, int gefundeneSchemata, Instant syncedAt) {
}
