package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/** Ein MariaDB-Schema einer Schule auf einer SVWS-Instanz (Mandant: über {@code schuleId}). */
public record Schema(
    UUID id,
    UUID schuleId,
    UUID instanzId,
    String schemaName,
    Umgebung umgebung,
    Instant createdAt,
    Instant updatedAt
) {
}
