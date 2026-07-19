package de.svws_nrw.edugate.control.schema.dto;

import de.svws_nrw.edugate.core.domain.SchemaSource;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import java.time.Instant;
import java.util.UUID;

/** API-Antwortformat für ein Schema/eine Schuldatenbank einer Schule (ADR-012). */
public record SchemaDto(
    UUID id,
    UUID schuleId,
    UUID instanzId,
    String schemaName,
    String umgebung,
    SchemaStatus status,
    boolean aktiv,
    String beschreibung,
    SchemaSource source,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
