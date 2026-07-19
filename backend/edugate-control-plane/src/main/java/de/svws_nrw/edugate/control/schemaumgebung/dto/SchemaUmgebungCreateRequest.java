package de.svws_nrw.edugate.control.schemaumgebung.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchemaUmgebungCreateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_]{0,49}$",
        message = "Der Name darf nur Buchstaben, Ziffern und Unterstriche enthalten (max. 50 Zeichen) - er wird "
            + "Teil des Schemanamens.")
    String name,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.")
    String beschreibung
) {
}
