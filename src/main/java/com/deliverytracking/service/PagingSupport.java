package com.deliverytracking.service;

import com.deliverytracking.dto.PageParams;
import com.deliverytracking.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * Builds a Spring Data {@link Pageable} from the raw {@code page}, {@code size},
 * {@code sortBy} and {@code direction} query parameters.
 *
 * <p>Sort fields are whitelisted per endpoint: sorting by a field the endpoint
 * does not support is rejected with a 400 instead of failing later with an
 * ambiguous/unknown property error from the persistence provider.</p>
 */
public final class PagingSupport {

    private PagingSupport() {
    }

    /**
     * @param params            raw query parameters from the controller
     * @param allowedSortFields entity property names this endpoint may sort by
     * @param defaultSortField  sort field used when {@code sortBy} is absent
     * @param defaultDirection  sort direction used when {@code direction} is absent
     */
    public static Pageable pageRequest(PageParams params, Set<String> allowedSortFields,
                                       String defaultSortField, String defaultDirection) {
        String direction = (params.direction() == null || params.direction().isBlank())
                ? defaultDirection : params.direction();
        Sort.Direction sortDirection = switch (direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BadRequestException("Invalid sort direction: " + params.direction());
        };

        String sortField = (params.sortBy() == null || params.sortBy().isBlank())
                ? defaultSortField : params.sortBy();
        if (!allowedSortFields.contains(sortField)) {
            throw new BadRequestException("Cannot sort by '" + sortField + "'");
        }

        return PageRequest.of(params.page(), params.size(), Sort.by(sortDirection, sortField));
    }
}
