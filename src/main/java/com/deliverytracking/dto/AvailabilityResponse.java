package com.deliverytracking.dto;

import com.deliverytracking.entity.AvailabilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Current availability of the authenticated delivery partner")
public class AvailabilityResponse {

    @Schema(description = "OFFLINE, AVAILABLE or BUSY", example = "AVAILABLE")
    private AvailabilityStatus availability;
}