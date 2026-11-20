package com.chubb.claims.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decisions")
@Getter @Setter @NoArgsConstructor
public class Decision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private UUID claimId;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionOutcome outcome;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (decidedAt == null) decidedAt = Instant.now();
    }
}
