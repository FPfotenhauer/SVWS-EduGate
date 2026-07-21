package de.svws_nrw.edugate.control.schuldatei.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Eine Schule aus dem Landes-Schuldatei-Referenzkatalog (ADR-020), angereichert um den Namen des
 * verknüpften Schulträger-Katalogeintrags, falls vorhanden. Rein referenziell - kein
 * EduGate-Betreiberbestand.
 */
public record SchuleKatalogDto(
    UUID id,
    String bundeslandkennung,
    String schulnummer,
    String schulname,
    String schultraegernummer,
    String schultraegername,
    String schulform,
    String strasse,
    String plz,
    String ort,
    String kreis,
    String telefon,
    String fax,
    String email,
    String homepage,
    LocalDate aufloesung,
    boolean aktiv,
    Instant lastSeenAt,
    Instant createdAt,
    Instant updatedAt
) {
}
