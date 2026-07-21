package de.svws_nrw.edugate.control.schultraeger.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * API-Antwortformat für einen Schulträger. Keine JPA-Entity, siehe ADR-003 (Schichtung).
 *
 * @param quelle Herkunft gemäß ADR-020 "Schritt 2": {@code LANDESLISTE}, {@code MANUELL} oder
 *               {@code SONDERFALL}
 */
public record SchultraegerDto(
    UUID id,
    String name,
    String traegernummer,
    String strasse,
    String plz,
    String ort,
    String beschreibung,
    boolean aktiv,
    UUID katalogId,
    String quelle,
    String sonderfallHinweis,
    Instant createdAt,
    Instant updatedAt
) {
}
