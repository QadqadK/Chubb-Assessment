package com.chubb.claims.api.dto;

import com.chubb.claims.domain.ClaimType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record PolicyView(
            UUID id,
            String policyNumber,
            ClaimType policyType,
            String marketCode,
            String currency,
            BigDecimal coverageLimit,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}
