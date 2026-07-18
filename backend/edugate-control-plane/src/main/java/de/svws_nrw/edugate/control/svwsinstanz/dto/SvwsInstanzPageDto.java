package de.svws_nrw.edugate.control.svwsinstanz.dto;

import java.util.List;

/** Seite einer paginierten SVWS-Instanzen-Liste (Default-Größe 25, maximal 100, siehe Resource). */
public record SvwsInstanzPageDto(
    List<SvwsInstanzDto> items,
    int page,
    int size,
    long totalElements
) {
}
