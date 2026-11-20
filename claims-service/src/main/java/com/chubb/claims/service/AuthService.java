package com.chubb.claims.service;

import com.chubb.claims.api.dto.AuthDtos.LoginRequest;
import com.chubb.claims.api.dto.AuthDtos.LoginResponse;
import com.chubb.claims.api.dto.AuthDtos.UserSummary;
import com.chubb.claims.domain.User;
import com.chubb.claims.repository.UserRepository;
import com.chubb.claims.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        JwtService.IssuedToken issued = jwtService.issue(user);
        return new LoginResponse(
                issued.token(),
                "Bearer",
                issued.expiresAt(),
                new UserSummary(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getMarketCode(),
                        user.getTeamId()));
    }
}
