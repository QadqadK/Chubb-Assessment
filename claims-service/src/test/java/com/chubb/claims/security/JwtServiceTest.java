package com.chubb.claims.security;

import com.chubb.claims.domain.Role;
import com.chubb.claims.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-123456";
    private static final String ISSUER = "https://claims-service.local";

    @Test
    void issuedTokenRoundTripsWithIdentityClaims() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtService service = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), ISSUER, 60);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("claimant.my@example.com");
        user.setFullName("Aisha Claimant");
        user.setRole(Role.CLAIMANT);
        user.setMarketCode("MY");

        JwtService.IssuedToken issued = service.issue(user);
        Jwt jwt = decoder.decode(issued.token());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getClaimAsString("name")).isEqualTo(user.getFullName());
        assertThat(jwt.getClaimAsString("market")).isEqualTo("MY");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CLAIMANT");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(issued.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }
}
