package de.svws_nrw.edugate.control.schule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchuleCreateRequest(
    @NotBlank(message = "Die Schulnummer darf nicht leer sein.")
    @Size(max = 100, message = "Die Schulnummer darf höchstens 100 Zeichen lang sein.")
    String schulnummer,
    @NotBlank(message = "Der Name darf nicht leer sein.") String name
) {
}
