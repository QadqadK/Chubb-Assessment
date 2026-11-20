package com.chubb.claims.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Claim aggregate root. Related aggregates are referenced by ID (ADR-004).
 * {@code version} gives optimistic locking so two officers cannot pick up the same claim.
 * State-transition behaviour is added in Phase 2.
 */
@Entity
@Table(name = "claims")
@Getter @Setter @NoArgsConstructor
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "market_code", nullable = false, length = 2)
    private String marketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location")
    private String incidentLocation;

    @Column(nullable = false, length = 4000)
    private String description;

    /** Motor/property specific fields (ADR-005). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "incident_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> incidentDetails = new HashMap<>();

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "claimed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimedAmount;

    /** Liability exposure while the claim is open. */
    @Column(name = "reserve_amount", precision = 15, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "assigned_officer_id")
    private UUID assignedOfficerId;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (submittedAt == null) submittedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
