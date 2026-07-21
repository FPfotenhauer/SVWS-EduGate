package de.svws_nrw.edugate.control.schuldatei.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ergebnis-/Statuszeile eines Schuldatei-Imports (ADR-020: "Stand der zuletzt importierten/
 * synchronisierten Landes-Schuldatei", "Anzeige von Importfehlern"). {@code fehlermeldung} ist
 * immer ein kurzer, sicherer Text ohne interne Details (ADR-006) - die NRW-Endpunkte benötigen
 * keine Zugangsdaten, es gibt hier also ohnehin keine Secrets, aber das Prinzip bleibt konsistent.
 */
public record SchuldateiImportStatusDto(
    UUID id,
    Instant gestartetAm,
    Instant beendetAm,
    boolean erfolgreich,
    String fehlermeldung,
    Integer anzahlSchulen,
    Integer anzahlSchultraeger,
    String ausgeloestVon
) {
}
