package com.deliverytracking.repository;

import com.deliverytracking.entity.Restaurant;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification} fragments for {@link Restaurant} queries.
 */
public final class RestaurantSpecs {

    private RestaurantSpecs() {
    }

    /**
     * Case-insensitive substring match on the restaurant name. The LIKE pattern
     * is escaped so {@code %}, {@code _} and {@code \} in the input are treated
     * literally, not as wildcards.
     */
    public static Specification<Restaurant> nameContains(String name) {
        String pattern = "%" + escapeLike(name.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
