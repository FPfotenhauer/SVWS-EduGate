package de.svws_nrw.edugate.control.schuldatei.dto;

import java.util.List;

public record SchuleKatalogPageDto(List<SchuleKatalogDto> items, int page, int size, long totalElements) {
}
