package com.chubb.claims.event;

import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned event contract written to the outbox and published to Kafka.
 * The event id is the consumer's idempotency key; claim id is the Kafka message key.
 */
public record ClaimEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID claimId,
        String reference,
        String marketCode,
        ClaimType claimType,
        ClaimStatus status,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String currency,
        BigDecimal claimedAmount,
        BigDecimal reserveAmount,
        UUID assignedOfficerId,
        UUID actorId,
        String note,
        Map<String, Object> data
) {
}
