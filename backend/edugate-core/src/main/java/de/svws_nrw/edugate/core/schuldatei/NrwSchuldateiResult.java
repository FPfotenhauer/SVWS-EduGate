package de.svws_nrw.edugate.core.schuldatei;

import java.util.List;

/**
 * Ergebnis eines {@link NrwSchuldateiClient#fetchSchuldatei()}-Aufrufs. {@code message} ist immer
 * ein kurzer, sicherer, deutschsprachiger Text (ADR-006), analog zu den SVWS-Privileged-API-
 * Ergebnistypen. Bei einem Fehlschlag sind {@code schulen}/{@code schultraeger} immer leer statt
 * {@code null}.
 */
public record NrwSchuldateiResult(
    boolean success,
    String message,
    List<NrwSchuleEintrag> schulen,
    List<NrwSchultraegerEintrag> schultraeger
) {

    public static NrwSchuldateiResult success(final List<NrwSchuleEintrag> schulen,
            final List<NrwSchultraegerEintrag> schultraeger) {
        return new NrwSchuldateiResult(true, "Schuldatei erfolgreich abgerufen.", List.copyOf(schulen),
            List.copyOf(schultraeger));
    }

    public static NrwSchuldateiResult failure(final String message) {
        return new NrwSchuldateiResult(false, message, List.of(), List.of());
    }
}
