package com.deliverytracking.dto;

import com.deliverytracking.entity.Order;
import com.deliverytracking.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "An order and its current delivery state")
public class OrderResponse {

    @Schema(description = "Order id", example = "1")
    private Long id;
    @Schema(description = "Id of the customer who placed the order", example = "2")
    private Long customerId;
    @Schema(description = "Id of the delivery partner assigned to the order, null until accepted", example = "3")
    private Long deliveryPartnerId;
    @Schema(description = "Id of the restaurant", example = "1")
    private Long restaurantId;
    @Schema(description = "Name of the restaurant", example = "Spice Garden")
    private String restaurantName;
    @Schema(description = "Latitude of the restaurant", example = "12.9716")
    private Double restaurantLat;
    @Schema(description = "Longitude of the restaurant", example = "77.5946")
    private Double restaurantLng;
    @Schema(description = "Current status of the order", example = "OUT_FOR_DELIVERY")
    private OrderStatus status;
    @Schema(description = "Total amount of the order", example = "19.98")
    private BigDecimal totalAmount;
    @Schema(description = "Delivery address", example = "221B Baker Street, London")
    private String deliveryAddress;
    @Schema(description = "When the order was placed (ISO-8601)", example = "2026-08-05T10:00:00Z")
    private Instant createdAt;
    @Schema(description = "When the order was last updated (ISO-8601)", example = "2026-08-05T10:15:00Z")
    private Instant updatedAt;

    @Schema(description = "Nearby online delivery partners, present on orders in PLACED state")
    @Builder.Default
    private List<NearbyPartner> nearbyPartners = List.of();

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .deliveryPartnerId(order.getDeliveryPartner() != null ? order.getDeliveryPartner().getId() : null)
                .restaurantId(order.getRestaurant().getId())
                .restaurantName(order.getRestaurant().getName())
                .restaurantLat(order.getRestaurant().getLat())
                .restaurantLng(order.getRestaurant().getLng())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(order.getDeliveryAddress())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
