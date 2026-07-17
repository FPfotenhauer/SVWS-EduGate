package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein laufender SVWS-Server-Prozess eines Schulträgers (Mandant: {@code schultraegerId}).
 * {@code credentialsEncrypted} enthält niemals Klartext-Zugangsdaten (ADR-006).
 */
public record SvwsInstanz(
    UUID id,
    UUID schultraegerId,
    String baseUrl,
    InstanzStatus status,
    byte[] credentialsEncrypted,
    Instant createdAt,
    Instant updatedAt
) {
}
