package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to place a new order")
public class PlaceOrderRequest {

    @NotNull
    @Schema(description = "Id of the restaurant to order from", example = "1")
    private Long restaurantId;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Delivery address for the order", example = "221B Baker Street, London")
    private String deliveryAddress;

    @NotNull
    @DecimalMin(value = "-90.0", message = "must be between -90.0 and 90.0")
    @DecimalMax(value = "90.0", message = "must be between -90.0 and 90.0")
    @Schema(description = "Latitude of the delivery destination, between -90 and 90", example = "18.5204")
    private Double deliveryLatitude;

    @NotNull
    @DecimalMin(value = "-180.0", message = "must be between -180.0 and 180.0")
    @DecimalMax(value = "180.0", message = "must be between -180.0 and 180.0")
    @Schema(description = "Longitude of the delivery destination, between -180 and 180", example = "73.8567")
    private Double deliveryLongitude;

    @Valid
    @NotEmpty
    @Schema(description = "One or more line items for the order")
    private List<OrderItemRequest> items;
}
