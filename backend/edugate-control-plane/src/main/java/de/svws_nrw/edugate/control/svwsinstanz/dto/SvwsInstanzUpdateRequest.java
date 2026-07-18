package de.svws_nrw.edugate.control.svwsinstanz.dto;

import de.svws_nrw.edugate.core.domain.InstanzStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SvwsInstanzUpdateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Base-URL darf nicht leer sein.")
    @Pattern(regexp = "^https?://\\S+$", message = "Die Base-URL muss eine gültige http(s)-URL sein.")
    String baseUrl,
    @NotNull(message = "Der Status darf nicht fehlen.") InstanzStatus status,
    boolean aktiv
) {
}
