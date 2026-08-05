package com.deliverytracking.dto;

import com.deliverytracking.entity.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A menu item of a restaurant")
public class MenuItemResponse {

    @Schema(description = "Menu item id", example = "10")
    private Long id;
    @Schema(description = "Id of the restaurant this item belongs to", example = "1")
    private Long restaurantId;
    @Schema(description = "Menu item name", example = "Paneer Tikka")
    private String name;
    @Schema(description = "Price in the restaurant's currency", example = "9.99")
    private BigDecimal price;

    public static MenuItemResponse from(MenuItem item) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .restaurantId(item.getRestaurant().getId())
                .name(item.getName())
                .price(item.getPrice())
                .build();
    }
}
