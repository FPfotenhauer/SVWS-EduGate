package de.svws_nrw.edugate.control.svwsinstanz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Betreiber-Eingabe zum kontrollierten Zuordnen eines unzugeordneten SVWS-Schema-Funds zu einer
 * Schule (ADR-014 Schritt 2). Schulträger und Schule müssen explizit ausgewählt werden - EduGate
 * rät niemals automatisch aus dem Schemanamen oder aus SVWS-Schulinformationen (ADR-012:
 * "Keine automatische Tenant-Zuordnung allein aus dem Schemanamen").
 */
public record SchemaFundZuordnungRequest(
    @NotNull(message = "Der Schulträger darf nicht fehlen.") UUID schultraegerId,
    @NotNull(message = "Die Schule darf nicht fehlen.") UUID schuleId,
    @NotBlank(message = "Die Umgebung darf nicht leer sein.")
    @Size(max = 50, message = "Die Umgebung darf höchstens 50 Zeichen lang sein.")
    String umgebung,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.")
    String beschreibung
) {
}
