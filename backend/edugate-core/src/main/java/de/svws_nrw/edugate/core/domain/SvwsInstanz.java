package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein laufender SVWS-Server-Prozess: eine mandantenübergreifende Betriebsressource des
 * Dienstleisters, keine 1:1-/1:n-Bindung an einen Schulträger (ADR-011). Mehrere Schulträger
 * können dieselbe Instanz über ihre Schemas gemeinsam nutzen; die Mandantenzuordnung entsteht
 * ausschließlich über {@code Schema} ({@code tenantId}, {@code schuleId}, {@code instanzId}).
 * {@code credentialsEncrypted} enthält niemals Klartext-Zugangsdaten (ADR-006).
 */
public record SvwsInstanz(
    UUID id,
    String name,
    String baseUrl,
    InstanzStatus status,
    byte[] credentialsEncrypted,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
