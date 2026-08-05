package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Raw pagination and sorting query parameters accepted by the controllers.
 * The services turn these into a Spring Data {@code Pageable} via
 * {@code PagingSupport}, validating the requested sort field and direction
 * against a per-endpoint whitelist.
 */
@Schema(description = "Pagination and sorting query parameters")
public record PageParams(
        @Schema(description = "Zero-based page number", example = "0") int page,
        @Schema(description = "Page size (1-100)", example = "10") int size,
        @Schema(description = "Field to sort by (whitelisted per endpoint)", example = "createdAt") String sortBy,
        @Schema(description = "Sort direction: asc or desc", example = "desc") String direction) {
}
