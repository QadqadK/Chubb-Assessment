package com.chubb.claims.security;

import com.chubb.claims.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public IssuedToken issue(User user) {
        // JWT timestamps have second precision; truncate so the response matches the token exactly.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()))
                .claim("market", user.getMarketCode());
        if (user.getTeamId() != null) {
            claims.claim("teamId", user.getTeamId().toString());
        }

        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()));

        return new IssuedToken(jwt.getTokenValue(), expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
