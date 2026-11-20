package com.chubb.insights.messaging;

import com.chubb.insights.domain.ClaimReadModel;
import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.DecisionOutcome;
import com.chubb.insights.domain.NotificationLog;
import com.chubb.insights.domain.ProcessedEvent;
import com.chubb.insights.event.ClaimEventEnvelope;
import com.chubb.insights.event.ClaimEventTypes;
import com.chubb.insights.repository.ClaimReadModelRepository;
import com.chubb.insights.repository.NotificationLogRepository;
import com.chubb.insights.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReadModelProjector {

    private final ClaimReadModelRepository claims;
    private final ProcessedEventRepository processedEvents;
    private final NotificationLogRepository notifications;

    /**
     * Idempotent fold. The processed-event insert and the projection update share a transaction.
     * Events carry occurredAt, so a cross-topic reorder cannot roll a claim's state backwards.
     */
    @Transactional
    public void apply(ClaimEventEnvelope envelope, String rawPayload) {
        if (processedEvents.existsById(envelope.eventId())) {
            return;
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(envelope.eventId());
        processed.setEventType(envelope.eventType());
        processed.setClaimId(envelope.claimId());
        processed.setOccurredAt(envelope.occurredAt());
        processed.setPayload(rawPayload);
        processedEvents.save(processed);

        Map<String, Object> data = envelope.data() == null ? Map.of() : envelope.data();
        ClaimReadModel model = claims.findById(envelope.claimId()).orElse(null);
        boolean created = model == null;
        if (created) {
            model = new ClaimReadModel();
            model.setClaimId(envelope.claimId());
            model.setSubmittedAt(envelope.occurredAt());
            model.setLastEventAt(Instant.EPOCH);
        }

        boolean stale = !created && !envelope.occurredAt().isAfter(model.getLastEventAt());
        if (stale) {
            fillMissing(model, envelope, data);
        } else {
            applyLatest(model, envelope, data);
        }

        claims.save(model);
        notify(envelope, data, model);
    }

    private void applyLatest(ClaimReadModel model, ClaimEventEnvelope envelope, Map<String, Object> data) {
        model.setReference(envelope.reference());
        model.setMarketCode(envelope.marketCode());
        if (envelope.claimType() != null) {
            model.setClaimType(envelope.claimType());
        }
        if (envelope.status() != null) {
            model.setStatus(envelope.status());
        }
        if (envelope.currency() != null) {
            model.setCurrency(envelope.currency());
        }
        if (envelope.claimedAmount() != null) {
            model.setClaimedAmount(envelope.claimedAmount());
        }
        if (envelope.reserveAmount() != null) {
            model.setReserveAmount(envelope.reserveAmount());
        }
        model.setAssignedOfficerId(envelope.assignedOfficerId());
        applyNames(model, data);

        switch (envelope.eventType()) {
            case ClaimEventTypes.CLAIM_SUBMITTED -> model.setSubmittedAt(envelope.occurredAt());
            case ClaimEventTypes.CLAIM_DECIDED -> {
                String outcome = string(data.get("outcome"));
                if (outcome != null) {
                    model.setDecisionOutcome(DecisionOutcome.valueOf(outcome));
                }
                BigDecimal settlement = decimal(data.get("settlementAmount"));
                if (settlement != null) {
                    model.setSettlementAmount(settlement);
                }
                model.setDecidedAt(envelope.occurredAt());
            }
            case ClaimEventTypes.CLAIM_SETTLED -> {
                model.setSettledAt(envelope.occurredAt());
                model.setClosedAt(envelope.occurredAt());
            }
            default -> {
            }
        }

        if (model.getStatus() != null && isTerminal(model.getStatus()) && model.getClosedAt() == null) {
            model.setClosedAt(envelope.occurredAt());
        }

        model.setLastEventAt(envelope.occurredAt());
    }

    private void fillMissing(ClaimReadModel model, ClaimEventEnvelope envelope, Map<String, Object> data) {
        if (ClaimEventTypes.CLAIM_SUBMITTED.equals(envelope.eventType())
                && (model.getSubmittedAt() == null || envelope.occurredAt().isBefore(model.getSubmittedAt()))) {
            model.setSubmittedAt(envelope.occurredAt());
        }
        if (model.getClaimantName() == null) {
            model.setClaimantName(string(data.get("claimantName")));
        }
        if (model.getAssignedOfficerId() == null && envelope.assignedOfficerId() != null) {
            model.setAssignedOfficerId(envelope.assignedOfficerId());
        }
        if (model.getAssignedOfficerName() == null) {
            model.setAssignedOfficerName(officerName(data));
        }
        if (ClaimEventTypes.CLAIM_DECIDED.equals(envelope.eventType()) && model.getDecidedAt() == null) {
            String outcome = string(data.get("outcome"));
            if (outcome != null) {
                model.setDecisionOutcome(DecisionOutcome.valueOf(outcome));
            }
            BigDecimal settlement = decimal(data.get("settlementAmount"));
            if (settlement != null) {
                model.setSettlementAmount(settlement);
            }
            model.setDecidedAt(envelope.occurredAt());
        }
        if (ClaimEventTypes.CLAIM_SETTLED.equals(envelope.eventType()) && model.getSettledAt() == null) {
            model.setSettledAt(envelope.occurredAt());
            model.setClosedAt(envelope.occurredAt());
        }
        if (envelope.status() != null && isTerminal(envelope.status()) && model.getClosedAt() == null) {
            model.setClosedAt(envelope.occurredAt());
        }
    }

    private void applyNames(ClaimReadModel model, Map<String, Object> data) {
        String claimantName = string(data.get("claimantName"));
        if (claimantName != null) {
            model.setClaimantName(claimantName);
        }
        String officerName = officerName(data);
        if (officerName != null) {
            model.setAssignedOfficerName(officerName);
        }
    }

    private void notify(ClaimEventEnvelope envelope, Map<String, Object> data, ClaimReadModel model) {
        String recipient;
        String message;
        switch (envelope.eventType()) {
            case ClaimEventTypes.CLAIM_SUBMITTED -> {
                recipient = "CLAIMANT";
                message = "Claim " + envelope.reference() + " received. We will keep you updated.";
            }
            case ClaimEventTypes.CLAIM_ASSIGNED -> {
                recipient = "OFFICER";
                String name = officerName(data);
                message = "Claim " + envelope.reference() + " assigned"
                        + (name == null ? "." : " to " + name + ".");
            }
            case ClaimEventTypes.INFO_REQUESTED -> {
                recipient = "CLAIMANT";
                message = "Additional information is required for claim " + envelope.reference() + ".";
            }
            case ClaimEventTypes.INFO_PROVIDED -> {
                recipient = "OFFICER";
                message = "The claimant responded on claim " + envelope.reference() + ".";
            }
            case ClaimEventTypes.CLAIM_DECIDED -> {
                recipient = "CLAIMANT";
                String outcome = model.getDecisionOutcome() == null ? "recorded" : model.getDecisionOutcome().name();
                message = "A decision was recorded on claim " + envelope.reference() + ": " + outcome + ".";
            }
            case ClaimEventTypes.CLAIM_SETTLED -> {
                recipient = "CLAIMANT";
                message = "Claim " + envelope.reference() + " has been settled.";
            }
            default -> {
                return;
            }
        }

        NotificationLog notification = new NotificationLog();
        notification.setClaimId(envelope.claimId());
        notification.setEventType(envelope.eventType());
        notification.setRecipientRole(recipient);
        notification.setMessage(message);
        notifications.save(notification);
    }

    private static boolean isTerminal(ClaimStatus status) {
        return status == ClaimStatus.REJECTED || status == ClaimStatus.SETTLED;
    }

    private static String officerName(Map<String, Object> data) {
        String name = string(data.get("assignedOfficerName"));
        if (name == null) {
            name = string(data.get("officerName"));
        }
        if (name == null) {
            name = string(data.get("requestedByName"));
        }
        return name;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }
}
