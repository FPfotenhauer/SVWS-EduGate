package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Schule eines Schulträgers (Mandant: {@code schultraegerId}).
 *
 * @param quelle Herkunft gemäß ADR-020: {@code LANDESLISTE}, {@code MANUELL} oder {@code SONDERFALL}
 */
public record Schule(
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
