package com.deliverytracking.dto;

import com.deliverytracking.entity.Role;
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
@Schema(description = "Result of a successful registration or login")
public class AuthResponse {

    @Schema(description = "JWT access token. Prefix with 'Bearer ' when calling protected endpoints.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbWl0QGV4YW1wbGUuY29tIiwicm9sZSI6IkNVU1RPTUVSIn0.AbCdEf12345")
    private String token;
    @Schema(description = "Token type, always 'Bearer'", example = "Bearer")
    private String tokenType;
    @Schema(description = "Token validity in seconds", example = "86400")
    private long expiresIn;
    @Schema(description = "Id of the created/authenticated user", example = "1")
    private Long userId;
    @Schema(description = "Full name of the user", example = "Amit Sharma")
    private String name;
    @Schema(description = "Email of the user", example = "amit@example.com")
    private String email;
    @Schema(description = "Role of the user", example = "CUSTOMER")
    private Role role;
}
