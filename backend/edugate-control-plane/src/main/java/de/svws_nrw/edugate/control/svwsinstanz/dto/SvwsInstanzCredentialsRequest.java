package de.svws_nrw.edugate.control.svwsinstanz.dto;

import jakarta.validation.constraints.NotBlank;

/** Zugangsdaten für den privilegierten SVWS-API-Zugriff auf eine Instanz (ADR-006). */
public record SvwsInstanzCredentialsRequest(
    @NotBlank(message = "Der Benutzername darf nicht leer sein.") String username,
    @NotBlank(message = "Das Passwort darf nicht leer sein.") String password
) {
}
