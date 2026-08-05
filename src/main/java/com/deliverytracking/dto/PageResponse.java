package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of results plus Spring Data pagination metadata.
 *
 * <p>Every paginated list endpoint returns this envelope instead of a raw
 * {@code List}, so clients know the total number of matching elements and pages
 * without issuing an extra counting request.</p>
 *
 * @param <T> the type of the items on the page
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A page of results with Spring Data pagination metadata")
public class PageResponse<T> {

    @Schema(description = "The items on this page")
    private List<T> content;

    @Schema(description = "Zero-based page number", example = "0")
    private int page;

    @Schema(description = "Number of items per page", example = "10")
    private int size;

    @Schema(description = "Total number of matching elements across all pages", example = "125")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "13")
    private int totalPages;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
