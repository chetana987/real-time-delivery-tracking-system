package com.deliverytracking.controller;

import com.deliverytracking.dto.ApiError;
import com.deliverytracking.dto.MenuItemRequest;
import com.deliverytracking.dto.MenuItemResponse;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.dto.RestaurantRequest;
import com.deliverytracking.dto.RestaurantResponse;
import com.deliverytracking.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
@Validated
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    @Operation(summary = "List all restaurants (paginated, filterable)",
            description = "Publicly available. Returns restaurants filtered by name and ordered as requested. "
                    + "Supports pagination (page/size) and sorting (sortBy/direction).",
            tags = "Restaurants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of restaurants with pagination metadata"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<RestaurantResponse> getAllRestaurants(
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(description = "Zero-based page number", example = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100)
            @Parameter(description = "Page size, between 1 and 100", example = "10") int size,
            @RequestParam(required = false)
            @Parameter(description = "Sort field. Allowed: id, name, address, lat, lng",
                    example = "name") String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Sort direction: asc or desc", example = "asc") String direction,
            @RequestParam(required = false)
            @Parameter(description = "Filter by restaurant name (case-insensitive substring)", example = "spice")
            String name) {
        return restaurantService.getAllRestaurants(name, new PageParams(page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a restaurant by id",
            description = "Publicly available. `id` must be a positive number.",
            tags = "Restaurants")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The requested restaurant",
                    content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public RestaurantResponse getRestaurant(@PathVariable @Positive Long id) {
        return restaurantService.getRestaurant(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a restaurant",
            description = "ADMIN only. Requires a JWT with role ADMIN.",
            tags = {"Restaurants", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Restaurant created",
                    content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request contains invalid fields (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public RestaurantResponse createRestaurant(@Valid @RequestBody RestaurantRequest request) {
        return restaurantService.createRestaurant(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a restaurant",
            description = "ADMIN only. Updates the fields of the restaurant with the given id.",
            tags = {"Restaurants", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Restaurant updated",
                    content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public RestaurantResponse updateRestaurant(@PathVariable @Positive Long id,
                                               @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.updateRestaurant(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a restaurant",
            description = "ADMIN only. Fails with 400 if the restaurant still has orders.",
            tags = {"Restaurants", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Restaurant deleted"),
            @ApiResponse(responseCode = "400", description = "Restaurant has orders and cannot be deleted",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void deleteRestaurant(@PathVariable @Positive Long id) {
        restaurantService.deleteRestaurant(id);
    }

    @GetMapping("/{id}/menu")
    @Operation(summary = "Get a restaurant's menu (paginated, filterable)",
            description = "Publicly available. Returns the menu items of a restaurant. Supports pagination "
                    + "(page/size), sorting (sortBy/direction) and filtering by item name and price range "
                    + "(minPrice/maxPrice).",
            tags = "Menu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of menu items with pagination metadata"),
            @ApiResponse(responseCode = "400", description = "Invalid path variable, query parameter or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<MenuItemResponse> getMenu(
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(description = "Zero-based page number", example = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100)
            @Parameter(description = "Page size, between 1 and 100", example = "10") int size,
            @RequestParam(required = false)
            @Parameter(description = "Sort field. Allowed: id, name, price", example = "price") String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Sort direction: asc or desc", example = "asc") String direction,
            @RequestParam(required = false)
            @Parameter(description = "Filter by menu item name (case-insensitive substring)", example = "burger")
            String itemName,
            @RequestParam(required = false)
            @Parameter(description = "Minimum price (inclusive)", example = "5.00") BigDecimal minPrice,
            @RequestParam(required = false)
            @Parameter(description = "Maximum price (inclusive)", example = "15.00") BigDecimal maxPrice) {
        return restaurantService.getMenu(id, itemName, minPrice, maxPrice,
                new PageParams(page, size, sortBy, direction));
    }

    @PostMapping("/{id}/menu")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a menu item",
            description = "ADMIN only. Adds an item to the restaurant's menu.",
            tags = {"Menu", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Menu item created",
                    content = @Content(schema = @Schema(implementation = MenuItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MenuItemResponse addMenuItem(@PathVariable @Positive Long id,
                                        @Valid @RequestBody MenuItemRequest request) {
        return restaurantService.addMenuItem(id, request);
    }

    @PutMapping("/{id}/menu/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a menu item",
            description = "ADMIN only. Updates the item belonging to the given restaurant.",
            tags = {"Menu", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Menu item updated",
                    content = @Content(schema = @Schema(implementation = MenuItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant or menu item not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MenuItemResponse updateMenuItem(@PathVariable @Positive Long id,
                                           @PathVariable @Positive Long itemId,
                                           @Valid @RequestBody MenuItemRequest request) {
        return restaurantService.updateMenuItem(id, itemId, request);
    }

    @DeleteMapping("/{id}/menu/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a menu item",
            description = "ADMIN only. Removes the item from the restaurant's menu.",
            tags = {"Menu", "Admin"})
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Menu item deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Restaurant or menu item not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void deleteMenuItem(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId) {
        restaurantService.deleteMenuItem(id, itemId);
    }
}
