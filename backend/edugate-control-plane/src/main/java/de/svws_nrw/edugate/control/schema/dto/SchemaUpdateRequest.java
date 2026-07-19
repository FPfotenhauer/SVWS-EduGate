package de.svws_nrw.edugate.control.schema.dto;

import de.svws_nrw.edugate.core.domain.SchemaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SchemaUpdateRequest(
    @NotNull(message = "Die SVWS-Instanz darf nicht fehlen.") UUID instanzId,
    @NotBlank(message = "Der Schemaname darf nicht leer sein.")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_]{0,63}$",
        message = "Der Schemaname darf nur Buchstaben, Ziffern und Unterstriche enthalten (max. 64 Zeichen, "
            + "MariaDB-kompatibel) - die Standardkonvention verwendet z. B. reine Schulnummern wie '123456'.")
    String schemaName,
    @NotBlank(message = "Die Umgebung darf nicht leer sein.")
    @Size(max = 50, message = "Die Umgebung darf höchstens 50 Zeichen lang sein.")
    String umgebung,
    @Size(max = 2000, message = "Die Beschreibung darf höchstens 2000 Zeichen lang sein.")
    String beschreibung,
    @NotNull(message = "Der Status darf nicht fehlen.") SchemaStatus status,
    boolean aktiv
) {
}
