package com.mateforge.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 40) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 120) String password
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record GoogleLoginRequest(
        @NotBlank String credential
    ) {
    }

    public record AuthResponse(
        String token,
        UUID userId,
        String username,
        String email
    ) {
    }
}
