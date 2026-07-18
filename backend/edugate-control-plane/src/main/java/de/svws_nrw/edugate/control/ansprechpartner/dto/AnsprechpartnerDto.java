package de.svws_nrw.edugate.control.ansprechpartner.dto;

import java.time.Instant;
import java.util.UUID;

/** API-Antwortformat für einen Ansprechpartner eines Schulträgers. */
public record AnsprechpartnerDto(
    UUID id,
    String name,
    String vorname,
    String titel,
    String abteilung,
    String funktion,
    String email,
    String telefonFestnetz,
    String telefonMobil,
    String beschreibung,
    Instant createdAt,
    Instant updatedAt
) {
}
