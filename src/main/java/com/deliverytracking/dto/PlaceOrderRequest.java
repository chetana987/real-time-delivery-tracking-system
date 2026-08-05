package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

    @Valid
    @NotEmpty
    @Schema(description = "One or more line items for the order")
    private List<OrderItemRequest> items;
}
