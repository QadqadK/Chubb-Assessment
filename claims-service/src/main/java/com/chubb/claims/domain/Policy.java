package com.chubb.claims.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Getter @Setter @NoArgsConstructor
public class Policy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 30)
    private String policyNumber;

    @Column(name = "holder_id", nullable = false)
    private UUID holderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 20)
    private ClaimType policyType;

    @Column(name = "market_code", nullable = false, length = 2)
    private String marketCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "coverage_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal coverageLimit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}
