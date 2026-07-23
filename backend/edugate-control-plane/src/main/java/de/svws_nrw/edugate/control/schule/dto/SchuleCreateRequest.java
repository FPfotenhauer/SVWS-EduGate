package de.svws_nrw.edugate.control.schule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * katalogId referenziert einen Eintrag aus dem Schuldatei-Katalog (ADR-020): Ist er gesetzt,
 * übernimmt der Server die Herkunft {@code LANDESLISTE}, unabhängig vom Wert von
 * {@code sonderfall}. Ohne katalogId markiert {@code sonderfall=true} eine Schule ohne
 * Katalogeintrag ({@code SONDERFALL}, mit optionalem sonderfallHinweis); ansonsten gilt
 * {@code MANUELL}.
 */
public record SchuleCreateRequest(
    @NotBlank(message = "Die Schulnummer darf nicht leer sein.")
    @Size(max = 100, message = "Die Schulnummer darf höchstens 100 Zeichen lang sein.")
    String schulnummer,
    @NotBlank(message = "Der Name darf nicht leer sein.") String name,
    UUID katalogId,
    boolean sonderfall,
    @Size(max = 2000, message = "Der Sonderfall-Hinweis darf höchstens 2000 Zeichen lang sein.") String sonderfallHinweis
) {
}
