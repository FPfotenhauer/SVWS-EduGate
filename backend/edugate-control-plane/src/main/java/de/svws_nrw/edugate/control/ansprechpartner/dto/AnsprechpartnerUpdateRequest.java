package de.svws_nrw.edugate.control.ansprechpartner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnsprechpartnerUpdateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Der Vorname darf nicht leer sein.") String vorname,
    @Size(max = 100, message = "Der Titel darf höchstens 100 Zeichen lang sein.") String titel,
    @Size(max = 200, message = "Die Abteilung darf höchstens 200 Zeichen lang sein.") String abteilung,
    @Size(max = 200, message = "Die Funktion darf höchstens 200 Zeichen lang sein.") String funktion,
    @Email(message = "Die E-Mail-Adresse ist ungültig.") String email,
    @Size(max = 50, message = "Die Telefonnummer darf höchstens 50 Zeichen lang sein.") String telefonFestnetz,
    @Size(max = 50, message = "Die Telefonnummer darf höchstens 50 Zeichen lang sein.") String telefonMobil,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.") String beschreibung
) {
}
