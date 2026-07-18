package de.svws_nrw.edugate.control.svwsinstanz.dto;

import de.svws_nrw.edugate.core.domain.InstanzStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * API-Antwortformat für eine SVWS-Instanz. Enthält niemals Klartext- oder verschlüsselte
 * Zugangsdaten (ADR-006) - {@code credentialsHinterlegt} zeigt nur an, ob welche gesetzt sind.
 */
public record SvwsInstanzDto(
    UUID id,
    String name,
    String baseUrl,
    InstanzStatus status,
    boolean aktiv,
    boolean credentialsHinterlegt,
    Instant createdAt,
    Instant updatedAt
) {
}
