package com.chubb.insights.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per claim, folded from the event stream. Optimised for manager/officer reads. */
@Entity
@Table(name = "claim_read_model")
@Getter @Setter @NoArgsConstructor
public class ClaimReadModel {

    @Id
    @Column(name = "claim_id")
    private UUID claimId;

    @Column(nullable = false, length = 30)
    private String reference;

    @Column(name = "market_code", nullable = false, length = 2)
    private String marketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "claimed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "reserve_amount", precision = 15, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private BigDecimal settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_outcome", length = 20)
    private DecisionOutcome decisionOutcome;

    @Column(name = "claimant_name", length = 150)
    private String claimantName;

    @Column(name = "assigned_officer_id")
    private UUID assignedOfficerId;

    @Column(name = "assigned_officer_name", length = 150)
    private String assignedOfficerName;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
