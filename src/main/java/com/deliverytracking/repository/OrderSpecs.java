package com.deliverytracking.repository;

import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Reusable {@link Specification} fragments for {@link Order} queries.
 * Filters are composed with {@code Specification.and(...)} in the service layer,
 * so any combination (including "no filters") can be expressed without
 * generating a new repository method per combination.
 */
public final class OrderSpecs {

    private OrderSpecs() {
    }

    /**
     * Always-true predicate. Used as a neutral base for roles that are not
     * scoped (e.g. ADMIN listing every order) so that additional filters can
     * be chained with {@code and(...)} regardless of role.
     */
    public static Specification<Order> any() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Order> customerId(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> deliveryPartnerId(Long deliveryPartnerId) {
        return (root, query, cb) -> cb.equal(root.get("deliveryPartner").get("id"), deliveryPartnerId);
    }

    public static Specification<Order> restaurantId(Long restaurantId) {
        return (root, query, cb) -> cb.equal(root.get("restaurant").get("id"), restaurantId);
    }

    public static Specification<Order> status(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Filters on the {@code createdAt} instant. Both bounds are optional and
     * interpreted as dates in UTC: {@code from} includes the whole day,
     * {@code to} includes the whole day (end exclusive at next midnight).
     */
    public static Specification<Order> createdAtBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            Instant start = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
            Instant end = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
            if (start != null && end != null) {
                return cb.between(root.get("createdAt"), start, end);
            }
            if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            }
            return cb.lessThan(root.get("createdAt"), end);
        };
    }
}
