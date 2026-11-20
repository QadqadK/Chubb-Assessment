package com.chubb.claims.api.dto;

import com.chubb.claims.api.dto.ClaimDtos.ClaimEventView;
import com.chubb.claims.api.dto.ClaimDtos.DecisionView;
import com.chubb.claims.api.dto.ClaimDtos.DocumentView;
import com.chubb.claims.api.dto.ClaimDtos.InfoRequestView;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;
import com.chubb.claims.domain.DecisionOutcome;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OfficerDtos {

    private OfficerDtos() {
    }

    public record RequestInformationRequest(
            @NotBlank @Size(max = 2000) String question
    ) {
    }

    public record RecordAssessmentRequest(
            @NotBlank @Size(max = 4000) String notes,
            @NotNull @DecimalMin("0.00") BigDecimal assessedAmount
    ) {
    }

    public record DecisionRequest(
            @NotNull DecisionOutcome outcome,
            @NotBlank @Size(max = 2000) String reason,
            @DecimalMin("0.00") BigDecimal settlementAmount
    ) {
    }

    public record QueueItem(
            UUID id,
            String reference,
            ClaimType claimType,
            ClaimStatus status,
            String marketCode,
            LocalDate incidentDate,
            String currency,
            BigDecimal claimedAmount,
            Instant submittedAt,
            long ageDays
    ) {
    }

    public record WorkloadResponse(
            UUID officerId,
            long totalOpen,
            Map<ClaimStatus, Long> byStatus,
            List<QueueItem> oldestOpen
    ) {
    }

    public record AssessmentView(
            UUID id,
            UUID officerId,
            String notes,
            BigDecimal assessedAmount,
            Instant createdAt
    ) {
    }

    public record OfficerClaimView(
            UUID id,
            String reference,
            ClaimType claimType,
            ClaimStatus status,
            String marketCode,
            LocalDate incidentDate,
            String incidentLocation,
            String description,
            Map<String, Object> incidentDetails,
            String currency,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            UUID claimantId,
            String claimantName,
            String claimantEmail,
            UUID assignedOfficerId,
            Instant submittedAt,
            Instant updatedAt,
            Instant closedAt,
            List<ClaimEventView> timeline,
            List<InfoRequestView> infoRequests,
            List<DocumentView> documents,
            AssessmentView latestAssessment,
            DecisionView decision
    ) {
    }
}
