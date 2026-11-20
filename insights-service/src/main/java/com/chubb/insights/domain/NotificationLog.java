package com.chubb.insights.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Stubbed notification delivery log. A real service would hand these to email/SMS. */
@Entity
@Table(name = "notification_log")
@Getter @Setter @NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "recipient_role", nullable = false, length = 20)
    private String recipientRole;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
