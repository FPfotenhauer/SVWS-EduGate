package de.svws_nrw.edugate.control.schultraeger.dto;

import jakarta.validation.constraints.NotBlank;

public record SchultraegerUpdateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Trägernummer darf nicht leer sein.") String traegernummer
) {
}
