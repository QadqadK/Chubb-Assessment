package com.chubb.insights;

import com.chubb.insights.domain.ClaimReadModel;
import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.ClaimType;
import com.chubb.insights.domain.DecisionOutcome;
import com.chubb.insights.event.ClaimEventEnvelope;
import com.chubb.insights.event.ClaimEventTypes;
import com.chubb.insights.messaging.ReadModelProjector;
import com.chubb.insights.repository.ClaimReadModelRepository;
import com.chubb.insights.repository.NotificationLogRepository;
import com.chubb.insights.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.kafka.topics-enabled=false"})
@Testcontainers
class ReadModelProjectorTest {

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID OFFICER_ID = UUID.randomUUID();
    private static final String REFERENCE = "CLM-MY-000900";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ReadModelProjector projector;
    @Autowired ClaimReadModelRepository claims;
    @Autowired ProcessedEventRepository processedEvents;
    @Autowired NotificationLogRepository notifications;

    @Test
    void foldsTheFullLifecycleAndIgnoresDuplicatesAndStaleEvents() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ClaimEventEnvelope submitted = envelope(ClaimEventTypes.CLAIM_SUBMITTED, ClaimStatus.SUBMITTED,
                null, ClaimStatus.SUBMITTED, now.minus(10, ChronoUnit.DAYS), null, null,
                Map.of("claimantName", "Aisha Claimant"));
        projector.apply(submitted, "{}");

        ClaimReadModel model = claims.findById(CLAIM_ID).orElseThrow();
        assertThat(model.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(model.getSubmittedAt()).isEqualTo(submitted.occurredAt());
        assertThat(model.getClaimantName()).isEqualTo("Aisha Claimant");
        assertThat(processedFor(CLAIM_ID)).isEqualTo(1);
        assertThat(notificationsFor(CLAIM_ID)).isEqualTo(1);

        projector.apply(submitted, "{}");
        assertThat(processedFor(CLAIM_ID)).isEqualTo(1);
        assertThat(notificationsFor(CLAIM_ID)).isEqualTo(1);

        projector.apply(envelope(ClaimEventTypes.CLAIM_ASSIGNED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, now.minus(9, ChronoUnit.DAYS),
                OFFICER_ID, null,
                Map.of("assignedOfficerId", OFFICER_ID, "assignedOfficerName", "Omar Officer")), "{}");
        projector.apply(envelope(ClaimEventTypes.INFO_REQUESTED, ClaimStatus.INFO_REQUESTED,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED, now.minus(8, ChronoUnit.DAYS),
                OFFICER_ID, null,
                Map.of("requestedByName", "Omar Officer", "question", "Police report?")), "{}");
        projector.apply(envelope(ClaimEventTypes.INFO_PROVIDED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.INFO_REQUESTED, ClaimStatus.UNDER_REVIEW, now.minus(7, ChronoUnit.DAYS),
                OFFICER_ID, null, Map.of()), "{}");
        projector.apply(envelope(ClaimEventTypes.CLAIM_ASSESSED, ClaimStatus.ASSESSED,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.ASSESSED, now.minus(6, ChronoUnit.DAYS),
                OFFICER_ID, new BigDecimal("4200.00"),
                Map.of("assessedAmount", new BigDecimal("4200.00"), "officerName", "Omar Officer")), "{}");
        projector.apply(envelope(ClaimEventTypes.CLAIM_DECIDED, ClaimStatus.APPROVED,
                ClaimStatus.ASSESSED, ClaimStatus.APPROVED, now.minus(5, ChronoUnit.DAYS),
                OFFICER_ID, new BigDecimal("4200.00"),
                Map.of("outcome", "APPROVED", "settlementAmount", new BigDecimal("4000.00"),
                        "officerName", "Omar Officer")), "{}");
        projector.apply(envelope(ClaimEventTypes.CLAIM_SETTLED, ClaimStatus.SETTLED,
                ClaimStatus.APPROVED, ClaimStatus.SETTLED, now.minus(4, ChronoUnit.DAYS),
                OFFICER_ID, new BigDecimal("4200.00"),
                Map.of("officerName", "Omar Officer")), "{}");

        model = claims.findById(CLAIM_ID).orElseThrow();
        assertThat(model.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(model.getReserveAmount()).isEqualByComparingTo("4200.00");
        assertThat(model.getSettlementAmount()).isEqualByComparingTo("4000.00");
        assertThat(model.getDecisionOutcome()).isEqualTo(DecisionOutcome.APPROVED);
        assertThat(model.getAssignedOfficerName()).isEqualTo("Omar Officer");
        assertThat(model.getDecidedAt()).isNotNull();
        assertThat(model.getSettledAt()).isNotNull();
        assertThat(model.getClosedAt()).isNotNull();
        assertThat(processedFor(CLAIM_ID)).isEqualTo(7);
        assertThat(notificationsFor(CLAIM_ID)).isEqualTo(6);

        projector.apply(envelope(ClaimEventTypes.CLAIM_ASSESSED, ClaimStatus.ASSESSED,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.ASSESSED, now.minus(30, ChronoUnit.DAYS),
                OFFICER_ID, new BigDecimal("100.00"), Map.of()), "{}");

        model = claims.findById(CLAIM_ID).orElseThrow();
        assertThat(model.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(processedFor(CLAIM_ID)).isEqualTo(8);
    }

    @Test
    void lateSubmissionFillsImmutableFieldsWithoutRegressingStatus() {
        UUID claimId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        projector.apply(envelopeFor(claimId, "CLM-MY-000901", ClaimEventTypes.CLAIM_ASSESSED,
                ClaimStatus.ASSESSED, ClaimStatus.UNDER_REVIEW, ClaimStatus.ASSESSED,
                now.minus(2, ChronoUnit.DAYS), OFFICER_ID, new BigDecimal("1000.00"), Map.of()), "{}");
        projector.apply(envelopeFor(claimId, "CLM-MY-000901", ClaimEventTypes.CLAIM_SUBMITTED,
                ClaimStatus.SUBMITTED, null, ClaimStatus.SUBMITTED, now.minus(5, ChronoUnit.DAYS),
                null, null, Map.of("claimantName", "Late Submitter")), "{}");

        ClaimReadModel model = claims.findById(claimId).orElseThrow();
        assertThat(model.getStatus()).isEqualTo(ClaimStatus.ASSESSED);
        assertThat(model.getSubmittedAt()).isEqualTo(now.minus(5, ChronoUnit.DAYS));
        assertThat(model.getClaimantName()).isEqualTo("Late Submitter");
    }

    private long processedFor(UUID claimId) {
        return processedEvents.findAll().stream().filter(event -> event.getClaimId().equals(claimId)).count();
    }

    private long notificationsFor(UUID claimId) {
        return notifications.findAll().stream().filter(log -> log.getClaimId().equals(claimId)).count();
    }

    private ClaimEventEnvelope envelope(String eventType,
                                        ClaimStatus status,
                                        ClaimStatus from,
                                        ClaimStatus to,
                                        Instant occurredAt,
                                        UUID officerId,
                                        BigDecimal reserve,
                                        Map<String, Object> data) {
        return envelopeFor(CLAIM_ID, REFERENCE, eventType, status, from, to, occurredAt, officerId, reserve, data);
    }

    private ClaimEventEnvelope envelopeFor(UUID claimId,
                                           String reference,
                                           String eventType,
                                           ClaimStatus status,
                                           ClaimStatus from,
                                           ClaimStatus to,
                                           Instant occurredAt,
                                           UUID officerId,
                                           BigDecimal reserve,
                                           Map<String, Object> data) {
        return new ClaimEventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                claimId,
                reference,
                "MY",
                ClaimType.MOTOR,
                status,
                from,
                to,
                "MYR",
                new BigDecimal("4500.00"),
                reserve,
                officerId,
                OFFICER_ID,
                null,
                data);
    }
}
