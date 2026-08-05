package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to create or update a menu item (ADMIN only)")
public class MenuItemRequest {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Menu item name", example = "Paneer Tikka")
    private String name;

    @NotNull
    @DecimalMin(value = "0.01")
    @Schema(description = "Price in the restaurant's currency, greater than zero", example = "9.99")
    private BigDecimal price;
}
