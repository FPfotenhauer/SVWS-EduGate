package de.svws_nrw.edugate.control.svwsinstanz.dto;

/**
 * Ergebnis des optionalen SchulInfo-Abrufs (ADR-014 Schritt 2). {@code success=false} ist ein
 * fachlich normaler, erwartbarer Fall (fehlende Rechte, keine Informationen im Schema,
 * Netzwerkfehler) und darf die manuelle Zuordnung eines Funds nicht blockieren - der Endpunkt
 * liefert dafür immer 200 mit diesem DTO, nie einen Fehlerstatus.
 */
public record SvwsSchulInfoResultDto(boolean success, String message, SvwsSchulInfoDto schulInfo) {
}
