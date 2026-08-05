package com.deliverytracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Payload to authenticate and obtain a JWT")
public class LoginRequest {

    @NotBlank
    @Email
    @Schema(description = "Registered email address", example = "amit@example.com")
    private String email;

    @NotBlank
    @Schema(description = "Account password", example = "secret123")
    private String password;
}
