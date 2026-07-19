package de.svws_nrw.edugate.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein SVWS-Schema / eine Schuldatenbank (ADR-012): der Datenbestand einer Schule für eine
 * bestimmte {@code umgebung} (z. B. Produktiv, Test, Schulung) auf einer {@code svws_instanz}.
 * Mandantenbezogen über {@code tenantId} (Schulträger der Schule), RLS-relevant. {@code umgebung}
 * ist bewusst freier Text statt eines geschlossenen Enums: Betreiber brauchen erweiterbare Werte
 * über die Startwerte hinaus (nur "PRODUKTIV" bleibt als Konstante fachlich ausgezeichnet, siehe
 * {@link SchemaNamingKonstanten#PRODUKTIV}). {@code status} ist davon getrennt der technische
 * Lebenszyklus ({@link SchemaStatus}).
 */
public record Schema(
    UUID id,
    UUID tenantId,
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
