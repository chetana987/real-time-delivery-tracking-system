package com.deliverytracking.controller;

import com.deliverytracking.dto.ApiError;
import com.deliverytracking.dto.LocationUpdateMessage;
import com.deliverytracking.dto.PageParams;
import com.deliverytracking.dto.PageResponse;
import com.deliverytracking.service.LocationUpdateService;
import com.deliverytracking.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderId}/location")
@RequiredArgsConstructor
@Validated
@Tag(name = "Location Tracking")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationUpdateService locationUpdateService;

    @GetMapping
    @Operation(summary = "Get the latest location of an order",
            description = "Returns the most recent delivery-partner location recorded for the order.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest known location (or null fields if none yet)",
                    content = @Content(schema = @Schema(implementation = LocationUpdateMessage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid path variable or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized for this order",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public LocationUpdateMessage getLatestLocation(@PathVariable @Positive Long orderId,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return locationUpdateService.getLatestLocation(orderId, principal.getId());
    }

    @GetMapping("/history")
    @Operation(summary = "Get the location history of an order (paginated)",
            description = "Returns the recorded location updates for the order, newest first by default. "
                    + "Supports pagination (page/size) and sorting (sortBy/direction, allowed fields: "
                    + "id, timestamp).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Page of location updates for the order"),
            @ApiResponse(responseCode = "400", description = "Invalid path variable, query parameter or validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized for this order",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<LocationUpdateMessage> getHistory(
            @PathVariable @Positive Long orderId,
            @RequestParam(defaultValue = "0") @Min(0)
            @Parameter(description = "Zero-based page number", example = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100)
            @Parameter(description = "Page size, between 1 and 100", example = "10") int size,
            @RequestParam(required = false)
            @Parameter(description = "Sort field. Allowed: id, timestamp", example = "timestamp") String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Sort direction: asc or desc", example = "desc") String direction,
            @AuthenticationPrincipal UserPrincipal principal) {
        return locationUpdateService.getLocationHistory(orderId, principal.getId(),
                new PageParams(page, size, sortBy, direction));
    }
}
