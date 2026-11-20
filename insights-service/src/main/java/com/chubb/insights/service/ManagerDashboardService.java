package com.chubb.insights.service;

import com.chubb.insights.api.dto.ManagerDtos.Attention;
import com.chubb.insights.api.dto.ManagerDtos.ClaimAttention;
import com.chubb.insights.api.dto.ManagerDtos.Dashboard;
import com.chubb.insights.api.dto.ManagerDtos.MarketPerformance;
import com.chubb.insights.api.dto.ManagerDtos.OfficerWorkload;
import com.chubb.insights.api.dto.ManagerDtos.Portfolio;
import com.chubb.insights.api.dto.ManagerDtos.StatusBreakdown;
import com.chubb.insights.api.dto.ManagerDtos.TypeBreakdown;
import com.chubb.insights.domain.ClaimReadModel;
import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.ClaimType;
import com.chubb.insights.domain.DecisionOutcome;
import com.chubb.insights.repository.ClaimReadModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates the read model into the questions a manager actually asks:
 * where is the backlog, where is the liability, who is overloaded and what needs attention first.
 *
 * In-memory aggregation is deliberate at demo scale; at volume these become SQL/OLAP projections.
 */
@Service
@RequiredArgsConstructor
public class ManagerDashboardService {

    private static final Map<String, String> MARKET_NAMES = Map.of(
            "MY", "Malaysia",
            "SG", "Singapore",
            "HK", "Hong Kong",
            "TH", "Thailand",
            "ID", "Indonesia",
            "VN", "Vietnam");

    private final ClaimReadModelRepository claims;

    @Transactional(readOnly = true)
    public Dashboard dashboard(String market) {
        Instant now = Instant.now();
        String filter = normalize(market);
        List<ClaimReadModel> all = claims.findAll();
        List<ClaimReadModel> scoped = filter == null
                ? all
                : all.stream().filter(claim -> filter.equalsIgnoreCase(claim.getMarketCode())).toList();
        List<ClaimReadModel> open = scoped.stream().filter(claim -> claim.getClosedAt() == null).toList();

        return new Dashboard(
                now,
                filter,
                portfolio(scoped, open, now),
                byMarket(scoped, now),
                byType(open, now),
                byStatus(open, now),
                officerWorkload(scoped, open, now),
                attention(open, now));
    }

    private Portfolio portfolio(List<ClaimReadModel> scoped, List<ClaimReadModel> open, Instant now) {
        Instant cutoff = now.minus(7, ChronoUnit.DAYS);
        long unassigned = open.stream().filter(claim -> claim.getAssignedOfficerId() == null).count();
        long awaitingInfo = open.stream().filter(claim -> claim.getStatus() == ClaimStatus.INFO_REQUESTED).count();
        long approvedNotSettled = open.stream().filter(claim -> claim.getStatus() == ClaimStatus.APPROVED).count();
        List<ClaimReadModel> settled = scoped.stream().filter(claim -> claim.getSettledAt() != null).toList();

        return new Portfolio(
                open.size(),
                unassigned,
                awaitingInfo,
                approvedNotSettled,
                sumByCurrency(open, ClaimReadModel::getReserveAmount),
                countByStatus(open),
                averageOpenAgeDays(open, now),
                oldestOpenAgeDays(open, now),
                submittedSince(scoped, cutoff),
                decidedSince(scoped, cutoff),
                settledSince(scoped, cutoff),
                averageCycleTimeDays(scoped),
                decisionRatePct(scoped, DecisionOutcome.APPROVED),
                decisionRatePct(scoped, DecisionOutcome.REJECTED),
                sumByCurrency(settled, ClaimReadModel::getSettlementAmount),
                sumByCurrency(settled, ClaimReadModel::getClaimedAmount),
                settlementRatios(settled));
    }

    private List<MarketPerformance> byMarket(List<ClaimReadModel> scoped, Instant now) {
        Instant cutoff = now.minus(7, ChronoUnit.DAYS);
        Map<String, List<ClaimReadModel>> grouped = scoped.stream()
                .collect(Collectors.groupingBy(ClaimReadModel::getMarketCode, TreeMap::new, Collectors.toList()));

        List<MarketPerformance> result = new ArrayList<>();
        for (Map.Entry<String, List<ClaimReadModel>> entry : grouped.entrySet()) {
            List<ClaimReadModel> marketClaims = entry.getValue();
            List<ClaimReadModel> open = marketClaims.stream().filter(claim -> claim.getClosedAt() == null).toList();
            result.add(new MarketPerformance(
                    entry.getKey(),
                    MARKET_NAMES.getOrDefault(entry.getKey(), entry.getKey()),
                    open.size(),
                    open.stream().filter(claim -> claim.getAssignedOfficerId() == null).count(),
                    sumByCurrency(open, ClaimReadModel::getReserveAmount),
                    agingOver7Days(open, now),
                    submittedSince(marketClaims, cutoff),
                    decidedSince(marketClaims, cutoff),
                    settledSince(marketClaims, cutoff),
                    averageOpenAgeDays(open, now),
                    oldestOpenAgeDays(open, now)));
        }
        return result;
    }

    private List<TypeBreakdown> byType(List<ClaimReadModel> open, Instant now) {
        Map<ClaimType, List<ClaimReadModel>> grouped = open.stream()
                .collect(Collectors.groupingBy(
                        ClaimReadModel::getClaimType, () -> new EnumMap<>(ClaimType.class), Collectors.toList()));

        List<TypeBreakdown> result = new ArrayList<>();
        for (ClaimType type : ClaimType.values()) {
            List<ClaimReadModel> claimsOfType = grouped.getOrDefault(type, List.of());
            result.add(new TypeBreakdown(
                    type,
                    claimsOfType.size(),
                    sumByCurrency(claimsOfType, ClaimReadModel::getReserveAmount),
                    agingOver7Days(claimsOfType, now),
                    averageOpenAgeDays(claimsOfType, now)));
        }
        return result;
    }

    private List<StatusBreakdown> byStatus(List<ClaimReadModel> open, Instant now) {
        Map<ClaimStatus, List<ClaimReadModel>> grouped = open.stream()
                .collect(Collectors.groupingBy(
                        ClaimReadModel::getStatus, () -> new EnumMap<>(ClaimStatus.class), Collectors.toList()));

        List<StatusBreakdown> result = new ArrayList<>();
        for (ClaimStatus status : ClaimStatus.values()) {
            List<ClaimReadModel> claimsOfStatus = grouped.getOrDefault(status, List.of());
            if (claimsOfStatus.isEmpty()) {
                continue;
            }
            result.add(new StatusBreakdown(
                    status,
                    claimsOfStatus.size(),
                    sumByCurrency(claimsOfStatus, ClaimReadModel::getReserveAmount),
                    oldestOpenAgeDays(claimsOfStatus, now)));
        }
        return result;
    }

    private List<OfficerWorkload> officerWorkload(List<ClaimReadModel> scoped, List<ClaimReadModel> open, Instant now) {
        Instant cutoff = now.minus(7, ChronoUnit.DAYS);
        Map<UUID, List<ClaimReadModel>> openByOfficer = open.stream()
                .filter(claim -> claim.getAssignedOfficerId() != null)
                .collect(Collectors.groupingBy(ClaimReadModel::getAssignedOfficerId));
        Map<UUID, List<ClaimReadModel>> allByOfficer = scoped.stream()
                .filter(claim -> claim.getAssignedOfficerId() != null)
                .collect(Collectors.groupingBy(ClaimReadModel::getAssignedOfficerId));

        List<OfficerWorkload> result = new ArrayList<>();
        for (Map.Entry<UUID, List<ClaimReadModel>> entry : openByOfficer.entrySet()) {
            UUID officerId = entry.getKey();
            List<ClaimReadModel> officerOpen = entry.getValue();
            List<ClaimReadModel> officerAll = allByOfficer.getOrDefault(officerId, List.of());
            String name = officerName(officerOpen, officerAll, officerId);
            String market = officerOpen.get(0).getMarketCode();
            long settledLast7 = officerAll.stream()
                    .filter(claim -> claim.getSettledAt() != null && !claim.getSettledAt().isBefore(cutoff))
                    .count();

            result.add(new OfficerWorkload(
                    officerId,
                    name,
                    market,
                    officerOpen.size(),
                    countByStatus(officerOpen),
                    agingOver7Days(officerOpen, now),
                    oldestOpenAgeDays(officerOpen, now),
                    settledLast7,
                    averageCycleTimeDays(officerAll)));
        }

        result.sort(Comparator.comparingLong(OfficerWorkload::openClaims)
                .thenComparingLong(OfficerWorkload::oldestOpenAgeDays)
                .reversed());
        return result;
    }

    private Attention attention(List<ClaimReadModel> open, Instant now) {
        Comparator<ClaimReadModel> oldestFirst = Comparator.comparing(ClaimReadModel::getSubmittedAt);

        List<ClaimAttention> unassigned = open.stream()
                .filter(claim -> claim.getAssignedOfficerId() == null)
                .sorted(oldestFirst)
                .limit(5)
                .map(claim -> toAttention(claim, now))
                .toList();
        List<ClaimAttention> awaitingInfo = open.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.INFO_REQUESTED)
                .sorted(oldestFirst)
                .limit(5)
                .map(claim -> toAttention(claim, now))
                .toList();
        List<ClaimAttention> approvedNotSettled = open.stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.APPROVED)
                .sorted(oldestFirst)
                .limit(5)
                .map(claim -> toAttention(claim, now))
                .toList();
        List<ClaimAttention> topExposure = open.stream()
                .filter(claim -> claim.getReserveAmount() != null)
                .sorted(Comparator.comparing(ClaimReadModel::getReserveAmount).reversed())
                .limit(5)
                .map(claim -> toAttention(claim, now))
                .toList();

        return new Attention(unassigned, awaitingInfo, approvedNotSettled, topExposure);
    }

    private static ClaimAttention toAttention(ClaimReadModel claim, Instant now) {
        return new ClaimAttention(
                claim.getClaimId(),
                claim.getReference(),
                claim.getMarketCode(),
                claim.getClaimType(),
                claim.getStatus(),
                ageDays(claim, now),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getReserveAmount(),
                claim.getAssignedOfficerName());
    }

    private static String officerName(List<ClaimReadModel> open, List<ClaimReadModel> all, UUID officerId) {
        return open.stream().map(ClaimReadModel::getAssignedOfficerName).filter(name -> name != null).findFirst()
                .or(() -> all.stream().map(ClaimReadModel::getAssignedOfficerName)
                        .filter(name -> name != null).findFirst())
                .orElse("Officer " + officerId.toString().substring(0, 8));
    }

    private static Map<String, BigDecimal> sumByCurrency(
            Collection<ClaimReadModel> claims, Function<ClaimReadModel, BigDecimal> value) {
        Map<String, BigDecimal> result = new TreeMap<>();
        for (ClaimReadModel claim : claims) {
            BigDecimal amount = value.apply(claim);
            if (amount != null) {
                result.merge(claim.getCurrency(), amount, BigDecimal::add);
            }
        }
        return result;
    }

    private static Map<ClaimStatus, Long> countByStatus(Collection<ClaimReadModel> claims) {
        Map<ClaimStatus, Long> result = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            result.put(status, 0L);
        }
        for (ClaimReadModel claim : claims) {
            result.merge(claim.getStatus(), 1L, Long::sum);
        }
        return result;
    }

    private static long submittedSince(List<ClaimReadModel> claims, Instant cutoff) {
        return claims.stream()
                .filter(claim -> claim.getSubmittedAt() != null && !claim.getSubmittedAt().isBefore(cutoff))
                .count();
    }

    private static long decidedSince(List<ClaimReadModel> claims, Instant cutoff) {
        return claims.stream()
                .filter(claim -> claim.getDecidedAt() != null && !claim.getDecidedAt().isBefore(cutoff))
                .count();
    }

    private static long settledSince(List<ClaimReadModel> claims, Instant cutoff) {
        return claims.stream()
                .filter(claim -> claim.getSettledAt() != null && !claim.getSettledAt().isBefore(cutoff))
                .count();
    }

    private static long agingOver7Days(List<ClaimReadModel> claims, Instant now) {
        return claims.stream().filter(claim -> ageDays(claim, now) > 7).count();
    }

    private static double averageOpenAgeDays(List<ClaimReadModel> claims, Instant now) {
        return round1(claims.stream().mapToDouble(claim -> ageInDays(claim, now)).average().orElse(0));
    }

    private static long oldestOpenAgeDays(List<ClaimReadModel> claims, Instant now) {
        return claims.stream().mapToLong(claim -> ageDays(claim, now)).max().orElse(0);
    }

    private static long ageDays(ClaimReadModel claim, Instant now) {
        return Math.max(0, ChronoUnit.DAYS.between(claim.getSubmittedAt(), now));
    }

    private static double ageInDays(ClaimReadModel claim, Instant now) {
        return Math.max(0, Duration.between(claim.getSubmittedAt(), now).toMinutes() / 1440.0);
    }

    private static Double averageCycleTimeDays(List<ClaimReadModel> claims) {
        List<ClaimReadModel> settled = claims.stream()
                .filter(claim -> claim.getSubmittedAt() != null && claim.getSettledAt() != null)
                .toList();
        if (settled.isEmpty()) {
            return null;
        }
        double average = settled.stream()
                .mapToDouble(claim -> Duration.between(claim.getSubmittedAt(), claim.getSettledAt()).toMinutes() / 1440.0)
                .average()
                .orElse(0);
        return round1(average);
    }

    private static Double decisionRatePct(List<ClaimReadModel> claims, DecisionOutcome outcome) {
        List<ClaimReadModel> decided = claims.stream()
                .filter(claim -> claim.getDecisionOutcome() != null)
                .toList();
        if (decided.isEmpty()) {
            return null;
        }
        long matching = decided.stream().filter(claim -> claim.getDecisionOutcome() == outcome).count();
        return round1(100.0 * matching / decided.size());
    }

    private static Map<String, BigDecimal> settlementRatios(List<ClaimReadModel> settled) {
        Map<String, BigDecimal> settledAmount = sumByCurrency(settled, ClaimReadModel::getSettlementAmount);
        Map<String, BigDecimal> claimedAmount = sumByCurrency(settled, ClaimReadModel::getClaimedAmount);

        Map<String, BigDecimal> result = new TreeMap<>();
        for (Map.Entry<String, BigDecimal> entry : claimedAmount.entrySet()) {
            if (entry.getValue().signum() > 0) {
                BigDecimal settledValue = settledAmount.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                result.put(entry.getKey(), settledValue
                        .multiply(BigDecimal.valueOf(100))
                        .divide(entry.getValue(), 1, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String normalize(String market) {
        if (market == null || market.isBlank()) {
            return null;
        }
        return market.trim().toUpperCase(Locale.ROOT);
    }
}
