package com.chubb.insights.api.dto;

import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ManagerDtos {

    private ManagerDtos() {
    }

    public record Dashboard(
            Instant generatedAt,
            String marketFilter,
            Portfolio portfolio,
            List<MarketPerformance> byMarket,
            List<TypeBreakdown> byType,
            List<StatusBreakdown> byStatus,
            List<OfficerWorkload> officerWorkload,
            Attention attention
    ) {
    }

    public record Portfolio(
            long openClaims,
            long unassignedClaims,
            long awaitingInformation,
            long approvedNotSettled,
            Map<String, BigDecimal> openReserveByCurrency,
            Map<ClaimStatus, Long> openByStatus,
            double averageOpenAgeDays,
            long oldestOpenAgeDays,
            long submittedLast7Days,
            long decidedLast7Days,
            long settledLast7Days,
            Double averageCycleTimeDays,
            Double approvalRatePct,
            Double rejectionRatePct,
            Map<String, BigDecimal> settledAmountByCurrency,
            Map<String, BigDecimal> claimedAmountSettledByCurrency,
            Map<String, BigDecimal> settlementRatioPctByCurrency
    ) {
    }

    public record MarketPerformance(
            String marketCode,
            String marketName,
            long openClaims,
            long unassignedClaims,
            Map<String, BigDecimal> openReserveByCurrency,
            long agingOver7Days,
            long submittedLast7Days,
            long decidedLast7Days,
            long settledLast7Days,
            double averageOpenAgeDays,
            long oldestOpenAgeDays
    ) {
    }

    public record TypeBreakdown(
            ClaimType claimType,
            long openClaims,
            Map<String, BigDecimal> openReserveByCurrency,
            long agingOver7Days,
            double averageOpenAgeDays
    ) {
    }

    public record StatusBreakdown(
            ClaimStatus status,
            long openClaims,
            Map<String, BigDecimal> openReserveByCurrency,
            long oldestOpenAgeDays
    ) {
    }

    public record OfficerWorkload(
            UUID officerId,
            String officerName,
            String marketCode,
            long openClaims,
            Map<ClaimStatus, Long> byStatus,
            long agingOver7Days,
            long oldestOpenAgeDays,
            long settledLast7Days,
            Double averageCycleTimeDays
    ) {
    }

    public record ClaimAttention(
            UUID claimId,
            String reference,
            String marketCode,
            ClaimType claimType,
            ClaimStatus status,
            long ageDays,
            String currency,
            BigDecimal claimedAmount,
            BigDecimal reserveAmount,
            String assignedOfficerName
    ) {
    }

    public record Attention(
            List<ClaimAttention> oldestUnassigned,
            List<ClaimAttention> awaitingInformation,
            List<ClaimAttention> approvedNotSettled,
            List<ClaimAttention> topExposure
    ) {
    }
}
