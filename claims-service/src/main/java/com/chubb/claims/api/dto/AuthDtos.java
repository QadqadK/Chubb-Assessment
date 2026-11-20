package com.chubb.claims.api.dto;

import com.chubb.claims.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            Role role,
            String marketCode,
            UUID teamId
    ) {
    }

    public record LoginResponse(
            String token,
            String tokenType,
            Instant expiresAt,
            UserSummary user
    ) {
    }
}
