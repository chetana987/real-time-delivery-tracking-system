package com.deliverytracking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Liveness check",
            description = "Publicly available. Returns a fixed UP status while the application is running.")
    @ApiResponse(responseCode = "200", description = "Application is up",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "delivery-tracking",
                "timestamp", Instant.now()
        );
    }
}
