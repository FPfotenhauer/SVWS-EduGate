package de.svws_nrw.edugate.control.schuldatei.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ein Schulträger aus dem Landes-Schuldatei-Referenzkatalog (ADR-020-Nachtrag). Rein
 * referenziell - kein EduGate-Betreiberbestand.
 */
public record SchultraegerKatalogDto(
    UUID id,
    String bundeslandkennung,
    String traegernummer,
    String traegername,
    String traegerschaftsart,
    String strasse,
    String plz,
    String ort,
    LocalDate aufloesung,
    boolean aktiv,
    Instant lastSeenAt,
    Instant createdAt,
    Instant updatedAt
) {
}
