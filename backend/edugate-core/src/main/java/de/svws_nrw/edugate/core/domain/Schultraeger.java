package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Mandanten-Wurzel der Hierarchie Schulträger → Schule → Schema (vgl. ARCHITECTURE.md Kap. 5).
 * Trägt bewusst keine {@code tenant_id}: die eigene {@code id} ist die Tenant-ID (ADR-008).
 */
public record Schultraeger(
    UUID id,
    String name,
    String traegernummer,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
