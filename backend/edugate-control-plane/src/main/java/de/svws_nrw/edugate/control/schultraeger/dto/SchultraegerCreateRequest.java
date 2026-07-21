package de.svws_nrw.edugate.control.schultraeger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * katalogId referenziert einen Eintrag aus dem Schuldatei-Katalog (ADR-020 "Schritt 2"): Ist er
 * gesetzt, übernimmt der Server die Herkunft {@code LANDESLISTE}, unabhängig vom Wert von
 * {@code sonderfall}. Ohne katalogId markiert {@code sonderfall=true} einen Schulträger ohne
 * Katalogeintrag ({@code SONDERFALL}, mit optionalem sonderfallHinweis); ansonsten gilt
 * {@code MANUELL}.
 */
public record SchultraegerCreateRequest(
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    @NotBlank(message = "Die Trägernummer darf nicht leer sein.") String traegernummer,
    @Size(max = 200, message = "Die Straße darf höchstens 200 Zeichen lang sein.") String strasse,
    @Size(max = 20, message = "Die PLZ darf höchstens 20 Zeichen lang sein.") String plz,
    @Size(max = 200, message = "Der Ort darf höchstens 200 Zeichen lang sein.") String ort,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.") String beschreibung,
    UUID katalogId,
    boolean sonderfall,
    @Size(max = 2000, message = "Der Sonderfall-Hinweis darf höchstens 2000 Zeichen lang sein.") String sonderfallHinweis
) {
}
