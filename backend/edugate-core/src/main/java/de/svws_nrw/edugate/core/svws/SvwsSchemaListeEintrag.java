package de.svws_nrw.edugate.core.svws;

/**
 * Ein Eintrag der SVWS-Privileged-API-Antwort {@code GET /api/schema/liste/svws}
 * ({@code SchemaListeEintrag} in {@code examples/open-api-privileged.json}): ein auf der
 * SVWS-Instanz technisch vorhandenes Schema, unabhängig davon, ob EduGate es kennt.
 *
 * <p>{@code name} und {@code username} sind laut OpenAPI-Spezifikation Pflichtfelder; die
 * übrigen Felder sind {@link Boolean}/{@link Long}, weil die SVWS-API sie als optional
 * beschreibt (Verhalten bei fehlendem Feld: {@code null}, nicht geraten).
 */
public record SvwsSchemaListeEintrag(
    String name,
    String username,
    Boolean isSvws,
    Long revision,
    Boolean isTainted,
    Boolean isInConfig,
    Boolean isDeactivated
) {
}
