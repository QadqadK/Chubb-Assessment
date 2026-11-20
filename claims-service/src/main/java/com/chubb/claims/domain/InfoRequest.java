package com.chubb.claims.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "info_requests")
@Getter @Setter @NoArgsConstructor
public class InfoRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 4000)
    private String response;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InfoRequestStatus status = InfoRequestStatus.OPEN;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
    }
}
