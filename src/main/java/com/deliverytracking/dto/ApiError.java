package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standard error response returned by the global exception handler")
public class ApiError {

    @Schema(description = "When the error occurred (ISO-8601)", example = "2026-08-05T10:00:00Z")
    private Instant timestamp;
    @Schema(description = "HTTP status code", example = "400")
    private int status;
    @Schema(description = "Short error category", example = "Validation Failed")
    private String error;
    @Schema(description = "Human-readable error message", example = "Request contains invalid fields")
    private String message;
    @Schema(description = "Request path that produced the error", example = "/api/auth/register")
    private String path;
    @Schema(description = "Field-level validation errors (field name -> message). Present for 400 validation responses.",
            example = "{\"email\":\"must be a valid email\",\"password\":\"must not be blank\"}")
    private Map<String, String> fieldErrors;
}
