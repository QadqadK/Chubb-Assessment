package com.chubb.claims.api;

import com.chubb.claims.api.dto.PolicyDtos.PolicyView;
import com.chubb.claims.security.CurrentUser;
import com.chubb.claims.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final ClaimService claimService;

    @GetMapping
    public List<PolicyView> mine(@AuthenticationPrincipal Jwt jwt) {
        return claimService.listPolicies(CurrentUser.from(jwt).id());
    }
}
