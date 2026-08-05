package com.deliverytracking.repository;

import com.deliverytracking.entity.MenuItem;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Reusable {@link Specification} fragments for {@link MenuItem} queries.
 */
public final class MenuItemSpecs {

    private MenuItemSpecs() {
    }

    public static Specification<MenuItem> restaurantId(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurant").get("id"), restaurantId);
    }

    /**
     * Case-insensitive substring match on the item name (LIKE pattern escaped,
     * see {@link RestaurantSpecs}).
     */
    public static Specification<MenuItem> nameContains(String name) {
        String pattern = "%" + escapeLike(name.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    public static Specification<MenuItem> priceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<MenuItem> priceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
