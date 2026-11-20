package com.chubb.claims.api.dto;

import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;
import com.chubb.claims.domain.DecisionOutcome;
import com.chubb.claims.domain.InfoRequestStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record CreateClaimRequest(
            @NotNull UUID policyId,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 255) String incidentLocation,
            @NotNull @DecimalMin("0.00") BigDecimal claimedAmount,
            Map<String, Object> incidentDetails
    ) {
    }

    public record RespondInfoRequestRequest(
            @NotBlank @Size(max = 4000) String response
    ) {
    }

    public record ClaimSummary(
            UUID id,
            String reference,
            ClaimType claimType,
            ClaimStatus status,
            String marketCode,
            LocalDate incidentDate,
            String currency,
            BigDecimal claimedAmount,
            Instant submittedAt,
            Instant updatedAt
    ) {
    }

    public record ClaimEventView(
            String eventType,
            ClaimStatus fromStatus,
            ClaimStatus toStatus,
            String note,
            Instant occurredAt
    ) {
    }

    public record InfoRequestView(
            UUID id,
            String question,
            String response,
            InfoRequestStatus status,
            Instant requestedAt,
            Instant respondedAt
    ) {
    }

    public record DocumentView(
            UUID id,
            String fileName,
            String contentType,
            long sizeBytes,
            Instant uploadedAt
    ) {
    }

    public record DecisionView(
            DecisionOutcome outcome,
            String reason,
            BigDecimal settlementAmount,
            Instant decidedAt
    ) {
    }

    public record ClaimDetail(
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
            Instant submittedAt,
            Instant updatedAt,
            Instant closedAt,
            List<ClaimEventView> timeline,
            List<InfoRequestView> infoRequests,
            List<DocumentView> documents,
            DecisionView decision
    ) {
    }
}
