package com.deliverytracking.dto;

import com.deliverytracking.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Optional filters for the "list my orders" endpoint. Every field is optional;
 * a null value means "no restriction on this dimension". The caller scope
 * (customer id for CUSTOMER, delivery partner id for DELIVERY_PARTNER) is
 * always applied on top of these filters.
 */
@Schema(description = "Optional filters for the order list endpoint")
public record OrderFilter(
        @Schema(description = "Filter by order status", example = "DELIVERED") OrderStatus status,
        @Schema(description = "Filter by customer id", example = "2") Long customerId,
        @Schema(description = "Filter by delivery partner id", example = "3") Long deliveryPartnerId,
        @Schema(description = "Filter by restaurant id", example = "1") Long restaurantId,
        @Schema(description = "Earliest order date (inclusive), yyyy-MM-dd", example = "2026-08-01") LocalDate from,
        @Schema(description = "Latest order date (inclusive), yyyy-MM-dd", example = "2026-08-05") LocalDate to) {
}
