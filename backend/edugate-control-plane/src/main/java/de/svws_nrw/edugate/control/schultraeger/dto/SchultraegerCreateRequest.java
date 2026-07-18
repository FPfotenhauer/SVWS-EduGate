package de.svws_nrw.edugate.control.schultraeger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchultraegerCreateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Trägernummer darf nicht leer sein.") String traegernummer,
    @Size(max = 200, message = "Die Straße darf höchstens 200 Zeichen lang sein.") String strasse,
    @Size(max = 20, message = "Die PLZ darf höchstens 20 Zeichen lang sein.") String plz,
    @Size(max = 200, message = "Der Ort darf höchstens 200 Zeichen lang sein.") String ort,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.") String beschreibung
) {
}
