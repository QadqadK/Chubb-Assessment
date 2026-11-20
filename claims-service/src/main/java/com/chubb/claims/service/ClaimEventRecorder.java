package com.chubb.claims.service;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimEvent;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.repository.ClaimEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single place that appends the claimant-visible timeline/audit record. */
@Component
@RequiredArgsConstructor
public class ClaimEventRecorder {

    private final ClaimEventRepository events;

    public void record(Claim claim,
                       String eventType,
                       ClaimStatus from,
                       ClaimStatus to,
                       UUID actorId,
                       String note) {
        ClaimEvent event = new ClaimEvent();
        event.setClaimId(claim.getId());
        event.setEventType(eventType);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorId(actorId);
        event.setNote(note);
        events.save(event);
    }
}
