package de.svws_nrw.edugate.control.svwsinstanz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SvwsInstanzCreateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Base-URL darf nicht leer sein.")
    @Pattern(regexp = "^https?://\\S+$", message = "Die Base-URL muss eine gültige http(s)-URL sein.")
    String baseUrl,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.")
    String beschreibung
) {
}
