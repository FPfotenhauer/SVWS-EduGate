package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/** Eine Schule eines Schulträgers (Mandant: {@code schultraegerId}). */
public record Schule(
    UUID id,
    UUID schultraegerId,
    String schulnummer,
    String name,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
