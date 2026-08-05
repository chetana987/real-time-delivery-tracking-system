package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A single line item in an order")
public class OrderItemRequest {

    @NotNull
    @Schema(description = "Id of the menu item to order", example = "10")
    private Long menuItemId;

    @NotNull
    @Min(1)
    @Schema(description = "Quantity, at least 1", example = "2")
    private Integer quantity;
}
