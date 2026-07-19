package de.svws_nrw.edugate.control.schule.dto;

import java.time.Instant;
import java.util.UUID;

/** API-Antwortformat für eine Schule eines Schulträgers. */
public record SchuleDto(
    UUID id,
    UUID schultraegerId,
    String schulnummer,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
}
