package de.svws_nrw.edugate.control.schemaumgebung.dto;

import java.time.Instant;
import java.util.UUID;

/** API-Antwortformat für eine vom Betreiber verwaltete Schema-Umgebung (ADR-012). */
public record SchemaUmgebungDto(
    UUID id,
    String name,
    boolean system,
    String beschreibung,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
