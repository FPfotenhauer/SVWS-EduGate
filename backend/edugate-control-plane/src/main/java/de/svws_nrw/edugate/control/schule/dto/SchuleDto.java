package de.svws_nrw.edugate.control.schule.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * API-Antwortformat für eine Schule eines Schulträgers.
 *
 * @param quelle Herkunft gemäß ADR-020: {@code LANDESLISTE}, {@code MANUELL} oder {@code SONDERFALL}
 */
public record SchuleDto(
    UUID id,
    UUID schultraegerId,
    String schulnummer,
    String name,
    boolean aktiv,
    UUID katalogId,
    String quelle,
    String sonderfallHinweis,
    Instant createdAt,
    Instant updatedAt
) {
}
