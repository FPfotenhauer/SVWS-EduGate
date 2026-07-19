package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine vom Betreiber gepflegte Schema-Umgebung (ADR-012: "Umgebung" ist eine erweiterbare
 * Betreiber-Konvention, kein geschlossenes Enum). Mandantenübergreifende Betriebsressource, kein
 * Mandanten-Objekt - analog zu {@link SvwsInstanz} (ADR-011). {@code system}-Einträge (z. B.
 * {@link SchemaNamingKonstanten#PRODUKTIV}) sind reserviert: Name und Aktiv-Status sind
 * unveränderlich.
 */
public record SchemaUmgebung(
    UUID id,
    String name,
    boolean system,
    String beschreibung,
    boolean aktiv,
    Instant createdAt,
    Instant updatedAt
) {
}
