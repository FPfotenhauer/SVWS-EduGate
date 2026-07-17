package de.svws_nrw.edugate.control.operator;

import java.util.UUID;

/**
 * Ergebnis einer fachlichen {@link OperatorOperation} inklusive der für den
 * {@code audit_admin}-Eintrag benötigten Angaben.
 *
 * @param value      das fachliche Ergebnis (z. B. das angelegte/gelesene DTO)
 * @param entityId   betroffene Entität, {@code null} bei echten Cross-Tenant-Listen
 * @param tenantId   betroffener Mandant, {@code null} bei echten Cross-Tenant-Listen (ADR-009)
 * @param detailsJson optionales JSONB-Detail (z. B. Filter), niemals Secrets oder Schülerdaten
 */
public record OperatorOutcome<T>(T value, UUID entityId, UUID tenantId, String detailsJson) {

    public static <T> OperatorOutcome<T> of(final T value, final UUID entityId) {
        return new OperatorOutcome<>(value, entityId, entityId, null);
    }

    public static <T> OperatorOutcome<T> crossTenant(final T value, final String detailsJson) {
        return new OperatorOutcome<>(value, null, null, detailsJson);
    }
}
