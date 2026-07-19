package de.svws_nrw.edugate.core.domain;

/**
 * Technischer/fachlicher Lebenszyklus-Status eines {@link Schema} (ADR-012), getrennt von dessen
 * {@code umgebung} (fachlicher Zweck, z. B. Produktiv/Test/Schulung).
 */
public enum SchemaStatus {
    /** Nur in EduGate angelegt, noch nicht auf dem SVWS-Server vorhanden. */
    GEPLANT,
    /** Beim Abgleich auf der SVWS-Instanz gefunden. */
    VORHANDEN,
    /** Aktiv nutzbar. */
    AKTIV,
    /** Auf SVWS-Seite deaktiviert. */
    DEAKTIVIERT,
    /** Revision/Version passt nicht zum Zielstand. */
    MIGRATION_ERFORDERLICH,
    /** Letzter Abgleich oder letzte Operation ist fehlgeschlagen. */
    FEHLER,
    /** Fachlich nicht mehr aktiv, aber historisch nachweisbar. */
    ARCHIVIERT
}
