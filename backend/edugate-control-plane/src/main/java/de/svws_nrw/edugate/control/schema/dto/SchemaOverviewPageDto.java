package de.svws_nrw.edugate.control.schema.dto;

import java.util.List;

/** Seite einer paginierten Schuldatenbanken-Übersicht (Default-Größe 25, maximal 100). */
public record SchemaOverviewPageDto(
    List<SchemaOverviewDto> items,
    int page,
    int size,
    long totalElements
) {
}
