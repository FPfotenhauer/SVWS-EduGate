package de.svws_nrw.edugate.control.schuldatei.dto;

import java.util.List;

public record SchultraegerKatalogPageDto(List<SchultraegerKatalogDto> items, int page, int size, long totalElements) {
}
