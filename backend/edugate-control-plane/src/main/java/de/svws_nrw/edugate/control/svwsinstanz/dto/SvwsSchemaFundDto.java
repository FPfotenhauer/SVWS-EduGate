package de.svws_nrw.edugate.control.svwsinstanz.dto;

import de.svws_nrw.edugate.control.svwsinstanz.SchemaFundZuordnungsStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Ein gespeicherter Sync-Fund der SVWS-Privileged-API {@code GET /api/schema/liste/svws}
 * (ADR-014 Stufe 1), angereichert um die Zuordnung zum EduGate-eigenen {@code schema}-Bestand
 * (ADR-013: "bekannte EduGate-Schemata, unzugeordnete Funde und Konflikte klar
 * unterscheidbar"). {@code schemaId}/{@code schuleId}/{@code schultraegerId} sind {@code null},
 * solange {@code zuordnungsStatus} {@code UNZUGEORDNET} ist.
 *
 * <p>Enthält bewusst keine Zugangsdaten oder sonstigen Secrets (ADR-006/ADR-012) - {@code
 * username} ist der auf der SVWS-Instanz konfigurierte MariaDB-Benutzername, kein Passwort.
 */
public record SvwsSchemaFundDto(
    UUID id,
    UUID instanzId,
    String schemaName,
    String username,
    Boolean isSvws,
    Long revision,
    Boolean isTainted,
    Boolean isInConfig,
    Boolean isDeactivated,
    Instant firstSeenAt,
    Instant lastSeenAt,
    SchemaFundZuordnungsStatus zuordnungsStatus,
    UUID schemaId,
    UUID schuleId,
    UUID schultraegerId
) {
}
