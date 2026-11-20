package com.chubb.claims.security;

import com.chubb.claims.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/** The authenticated principal, reconstructed from JWT claims. */
public record CurrentUser(UUID id, String email, String fullName, Role role, String marketCode) {

    public static CurrentUser from(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalStateException("No authenticated JWT present");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("JWT is missing the roles claim");
        }
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                Role.valueOf(roles.get(0)),
                jwt.getClaimAsString("market"));
    }
}
