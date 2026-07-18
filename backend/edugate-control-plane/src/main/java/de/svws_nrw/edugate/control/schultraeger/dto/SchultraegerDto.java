package de.svws_nrw.edugate.control.schultraeger.dto;

import java.time.Instant;
import java.util.UUID;

/** API-Antwortformat für einen Schulträger. Keine JPA-Entity, siehe ADR-003 (Schichtung). */
public record SchultraegerDto(
    UUID id,
    String name,
    String traegernummer,
    String strasse,
    String plz,
    String ort,
    String beschreibung,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
