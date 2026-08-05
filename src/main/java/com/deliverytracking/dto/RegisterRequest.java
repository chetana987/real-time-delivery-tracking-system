package com.deliverytracking.dto;

import com.deliverytracking.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Payload to create a new user account")
public class RegisterRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Full name of the user", example = "Amit Sharma")
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Unique email address, also used as login", example = "amit@example.com")
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    @Schema(description = "Password, at least 6 characters", example = "secret123")
    private String password;

    @Size(max = 20)
    @Schema(description = "Optional phone number", example = "+91 98765 43210")
    private String phone;

    @NotNull
    @Schema(description = "Role of the new account. ADMIN cannot be self-registered",
            example = "CUSTOMER", allowableValues = {"CUSTOMER", "DELIVERY_PARTNER"})
    private Role role;
}
