
package com.clearclose.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record SignupRequest(
        @NotBlank String name,

        @NotBlank String username,

        @NotBlank
        @Size(min = 8)
        String password,

        @NotBlank
        @Email
        String email,

        String phone,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$")
        String panCard,

        String location
    ) {}

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    public record AuthResponse(
        String token,
        String tokenType,
        String accountType,
        String role,
        Long userId,
        String username
    ) {}
}
