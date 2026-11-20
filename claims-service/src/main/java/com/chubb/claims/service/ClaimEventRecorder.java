package com.chubb.claims.service;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimEvent;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.OutboxEvent;
import com.chubb.claims.event.ClaimEventEnvelope;
import com.chubb.claims.event.ClaimEventTopics;
import com.chubb.claims.repository.ClaimEventRepository;
import com.chubb.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Appends the claimant-visible timeline and, when the event has a Kafka topic, writes the outbox row.
 * Callers invoke this inside the same transaction as the state change.
 */
@Component
@RequiredArgsConstructor
public class ClaimEventRecorder {

    private static final int SCHEMA_VERSION = 1;

    private final ClaimEventRepository events;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public void record(Claim claim,
                       String eventType,
                       ClaimStatus from,
                       ClaimStatus to,
                       UUID actorId,
                       String note) {
        record(claim, eventType, from, to, actorId, note, Map.of());
    }

    public void record(Claim claim,
                       String eventType,
                       ClaimStatus from,
                       ClaimStatus to,
                       UUID actorId,
                       String note,
                       Map<String, Object> data) {
        ClaimEvent event = new ClaimEvent();
        event.setClaimId(claim.getId());
        event.setEventType(eventType);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorId(actorId);
        event.setNote(note);
        events.save(event);

        if (ClaimEventTopics.topicFor(eventType) == null) {
            return;
        }

        ClaimEventEnvelope envelope = new ClaimEventEnvelope(
                UUID.randomUUID(),
                eventType,
                SCHEMA_VERSION,
                Instant.now(),
                claim.getId(),
                claim.getReference(),
                claim.getMarketCode(),
                claim.getClaimType(),
                claim.getStatus(),
                from,
                to,
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getReserveAmount(),
                claim.getAssignedOfficerId(),
                actorId,
                note,
                data == null ? Map.of() : data);

        OutboxEvent row = new OutboxEvent();
        row.setAggregateId(claim.getId());
        row.setEventType(eventType);
        try {
            row.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise event " + eventType, ex);
        }
        outbox.save(row);
    }
}
