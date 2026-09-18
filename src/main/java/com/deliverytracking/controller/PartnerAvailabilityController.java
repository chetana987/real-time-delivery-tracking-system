package com.deliverytracking.controller;

import com.deliverytracking.dto.ApiError;
import com.deliverytracking.dto.AvailabilityResponse;
import com.deliverytracking.service.PartnerAvailabilityService;
import com.deliverytracking.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
@Tag(name = "Partners")
@SecurityRequirement(name = "bearerAuth")
public class PartnerAvailabilityController {

    private final PartnerAvailabilityService availabilityService;

    @GetMapping("/availability")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Get my delivery-partner availability",
            description = "Delivery partner only. Returns OFFLINE when the partner has no open WebSocket "
                    + "session, BUSY while the partner has an in-progress delivery (ACCEPTED, PICKED_UP or "
                    + "OUT_FOR_DELIVERY), and AVAILABLE otherwise.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current availability status",
                    content = @Content(schema = @Schema(implementation = AvailabilityResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but not a DELIVERY_PARTNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AvailabilityResponse getMyAvailability(@AuthenticationPrincipal UserPrincipal principal) {
        return new AvailabilityResponse(availabilityService.getStatus(principal.getId()));
    }
}