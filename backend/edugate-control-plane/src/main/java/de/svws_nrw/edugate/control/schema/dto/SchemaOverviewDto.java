package de.svws_nrw.edugate.control.schema.dto;

import de.svws_nrw.edugate.core.domain.InstanzStatus;
import de.svws_nrw.edugate.core.domain.SchemaSource;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Mandantenübergreifende Betreiber-Übersichtszeile einer Schuldatenbank (ADR-013): verdichtet
 * {@code schema} mit den zugehörigen Namen aus {@code schule}, {@code schultraeger} und
 * {@code svws_instanz}, damit die Betreiber-UI die Ketten "Instanz -> Schemata -> Schule ->
 * Schulträger" und "Schulträger/Schule -> Schemata -> Instanz" ohne Einzelabfragen darstellen
 * kann. Nur lesend, ausschließlich über {@link de.svws_nrw.edugate.control.operator.OperatorAccess}
 * erreichbar (ADR-009: "Mandantenübergreifende Listen, Suchen und Reports des
 * Dienstleister-Admins").
 */
public record SchemaOverviewDto(
    UUID id,
    String schemaName,
    String umgebung,
    SchemaStatus status,
    boolean aktiv,
    String beschreibung,
    SchemaSource source,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID schuleId,
    String schulnummer,
    String schuleName,
    UUID schultraegerId,
    String schultraegerName,
    UUID instanzId,
    String instanzName,
    String instanzBaseUrl,
    InstanzStatus instanzStatus
) {
}
