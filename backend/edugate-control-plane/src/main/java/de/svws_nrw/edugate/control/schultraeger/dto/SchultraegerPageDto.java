package de.svws_nrw.edugate.control.schultraeger.dto;

import java.util.List;

/** Seite einer paginierten Schulträger-Liste (Default-Größe 25, maximal 100, siehe Resource). */
public record SchultraegerPageDto(
    List<SchultraegerDto> items,
    int page,
    int size,
    long totalElements
) {
}
