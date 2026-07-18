package de.svws_nrw.edugate.control.svwsinstanz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SvwsInstanzCreateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Base-URL darf nicht leer sein.")
    @Pattern(regexp = "^https?://\\S+$", message = "Die Base-URL muss eine gültige http(s)-URL sein.")
    String baseUrl
) {
}
