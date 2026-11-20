package com.chubb.insights.event;

import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.ClaimType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Copy of the claims-service contract. In a larger system this would live in a shared module or be generated
 * from a schema registry; unknown fields are ignored so the consumer can tolerate additive changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
