package com.chubb.insights;

import com.chubb.insights.api.dto.ManagerDtos.Dashboard;
import com.chubb.insights.domain.ClaimReadModel;
import com.chubb.insights.domain.ClaimStatus;
import com.chubb.insights.domain.ClaimType;
import com.chubb.insights.domain.DecisionOutcome;
import com.chubb.insights.repository.ClaimReadModelRepository;
import com.chubb.insights.service.ManagerDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerDashboardServiceTest {

    private static final UUID OFFICER_ONE = UUID.randomUUID();
    private static final UUID OFFICER_TWO = UUID.randomUUID();

    @Mock ClaimReadModelRepository claims;
    @InjectMocks ManagerDashboardService service;

    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<ClaimReadModel> data = List.of(
                claim("CLM-MY-000001", "MY", ClaimType.MOTOR, ClaimStatus.SUBMITTED, "MYR",
                        "1000.00", "500.00", null, null, null,
                        now.minus(10, ChronoUnit.DAYS), null, null, null, null, null),
                claim("CLM-MY-000002", "MY", ClaimType.MOTOR, ClaimStatus.UNDER_REVIEW, "MYR",
                        "3000.00", "2000.00", OFFICER_ONE, "Omar Officer", null,
                        now.minus(3, ChronoUnit.DAYS), null, null, null, null, null),
                claim("CLM-MY-000003", "MY", ClaimType.PROPERTY, ClaimStatus.INFO_REQUESTED, "MYR",
                        "9000.00", "5000.00", OFFICER_ONE, "Omar Officer", null,
                        now.minus(15, ChronoUnit.DAYS), null, null, null, null, null),
                claim("CLM-MY-000004", "MY", ClaimType.MOTOR, ClaimStatus.SETTLED, "MYR",
                        "1000.00", "1000.00", OFFICER_ONE, "Omar Officer", "800.00",
                        now.minus(20, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS),
                        now.minus(2, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS),
                        DecisionOutcome.APPROVED, "Aisha Claimant"),
                claim("CLM-SG-000001", "SG", ClaimType.MOTOR, ClaimStatus.APPROVED, "SGD",
                        "2000.00", "1000.00", OFFICER_TWO, "Wei Officer", null,
                        now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
                        null, null, DecisionOutcome.APPROVED, "Ben Claimant"));
        when(claims.findAll()).thenReturn(data);
    }

    @Test
    void dashboardAnswersWhereToAllocatePeopleAndAttention() {
        Dashboard dashboard = service.dashboard(null);

        assertThat(dashboard.portfolio().openClaims()).isEqualTo(4);
        assertThat(dashboard.portfolio().unassignedClaims()).isEqualTo(1);
        assertThat(dashboard.portfolio().awaitingInformation()).isEqualTo(1);
        assertThat(dashboard.portfolio().approvedNotSettled()).isEqualTo(1);
        assertThat(dashboard.portfolio().openReserveByCurrency())
                .containsEntry("MYR", new BigDecimal("7500.00"))
                .containsEntry("SGD", new BigDecimal("1000.00"));
        assertThat(dashboard.portfolio().averageOpenAgeDays()).isCloseTo(7.3, within(0.1));
        assertThat(dashboard.portfolio().oldestOpenAgeDays()).isEqualTo(15);
        assertThat(dashboard.portfolio().submittedLast7Days()).isEqualTo(2);
        assertThat(dashboard.portfolio().decidedLast7Days()).isEqualTo(2);
        assertThat(dashboard.portfolio().settledLast7Days()).isEqualTo(1);
        assertThat(dashboard.portfolio().averageCycleTimeDays()).isCloseTo(18.0, within(0.1));
        assertThat(dashboard.portfolio().approvalRatePct()).isEqualTo(100.0);
        assertThat(dashboard.portfolio().rejectionRatePct()).isEqualTo(0.0);
        assertThat(dashboard.portfolio().settledAmountByCurrency()).containsEntry("MYR", new BigDecimal("800.00"));
        assertThat(dashboard.portfolio().claimedAmountSettledByCurrency())
                .containsEntry("MYR", new BigDecimal("1000.00"));
        assertThat(dashboard.portfolio().settlementRatioPctByCurrency()).containsEntry("MYR", new BigDecimal("80.0"));

        assertThat(dashboard.byMarket()).hasSize(2);
        assertThat(dashboard.byMarket().get(0).marketCode()).isEqualTo("MY");
        assertThat(dashboard.byMarket().get(0).marketName()).isEqualTo("Malaysia");
        assertThat(dashboard.byMarket().get(0).openClaims()).isEqualTo(3);
        assertThat(dashboard.byMarket().get(0).agingOver7Days()).isEqualTo(2);
        assertThat(dashboard.byMarket().get(0).settledLast7Days()).isEqualTo(1);
        assertThat(dashboard.byMarket().get(1).marketCode()).isEqualTo("SG");
        assertThat(dashboard.byMarket().get(1).openClaims()).isEqualTo(1);

        assertThat(dashboard.byType()).extracting(breakdown -> breakdown.claimType())
                .containsExactly(ClaimType.MOTOR, ClaimType.PROPERTY);
        assertThat(dashboard.byStatus()).extracting(breakdown -> breakdown.status())
                .containsExactly(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                        ClaimStatus.INFO_REQUESTED, ClaimStatus.APPROVED);

        assertThat(dashboard.officerWorkload()).hasSize(2);
        assertThat(dashboard.officerWorkload().get(0).officerId()).isEqualTo(OFFICER_ONE);
        assertThat(dashboard.officerWorkload().get(0).officerName()).isEqualTo("Omar Officer");
        assertThat(dashboard.officerWorkload().get(0).openClaims()).isEqualTo(2);
        assertThat(dashboard.officerWorkload().get(0).agingOver7Days()).isEqualTo(1);
        assertThat(dashboard.officerWorkload().get(0).oldestOpenAgeDays()).isEqualTo(15);
        assertThat(dashboard.officerWorkload().get(0).settledLast7Days()).isEqualTo(1);

        assertThat(dashboard.attention().oldestUnassigned()).singleElement()
                .extracting(claim -> claim.reference()).isEqualTo("CLM-MY-000001");
        assertThat(dashboard.attention().awaitingInformation()).singleElement()
                .extracting(claim -> claim.reference()).isEqualTo("CLM-MY-000003");
        assertThat(dashboard.attention().approvedNotSettled()).singleElement()
                .extracting(claim -> claim.reference()).isEqualTo("CLM-SG-000001");
        assertThat(dashboard.attention().topExposure()).extracting(claim -> claim.reference())
                .startsWith("CLM-MY-000003");
    }

    @Test
    void marketFilterScopesEverything() {
        Dashboard dashboard = service.dashboard("sg");

        assertThat(dashboard.marketFilter()).isEqualTo("SG");
        assertThat(dashboard.portfolio().openClaims()).isEqualTo(1);
        assertThat(dashboard.portfolio().openReserveByCurrency()).containsOnlyKeys("SGD");
        assertThat(dashboard.portfolio().settledLast7Days()).isZero();
        assertThat(dashboard.portfolio().averageCycleTimeDays()).isNull();
        assertThat(dashboard.byMarket()).hasSize(1);
        assertThat(dashboard.byMarket().get(0).marketCode()).isEqualTo("SG");
        assertThat(dashboard.officerWorkload()).singleElement()
                .extracting(workload -> workload.officerId()).isEqualTo(OFFICER_TWO);
    }

    private ClaimReadModel claim(String reference,
                                 String market,
                                 ClaimType type,
                                 ClaimStatus status,
                                 String currency,
                                 String claimed,
                                 String reserve,
                                 UUID officerId,
                                 String officerName,
                                 String settlement,
                                 Instant submittedAt,
                                 Instant decidedAt,
                                 Instant settledAt,
                                 Instant closedAt,
                                 DecisionOutcome outcome,
                                 String claimantName) {
        ClaimReadModel claim = new ClaimReadModel();
        claim.setClaimId(UUID.randomUUID());
        claim.setReference(reference);
        claim.setMarketCode(market);
        claim.setClaimType(type);
        claim.setStatus(status);
        claim.setCurrency(currency);
        claim.setClaimedAmount(new BigDecimal(claimed));
        claim.setReserveAmount(reserve == null ? null : new BigDecimal(reserve));
        claim.setSettlementAmount(settlement == null ? null : new BigDecimal(settlement));
        claim.setDecisionOutcome(outcome);
        claim.setClaimantName(claimantName);
        claim.setAssignedOfficerId(officerId);
        claim.setAssignedOfficerName(officerName);
        claim.setSubmittedAt(submittedAt);
        claim.setLastEventAt(settledAt != null ? settledAt : submittedAt);
        claim.setDecidedAt(decidedAt);
        claim.setSettledAt(settledAt);
        claim.setClosedAt(closedAt);
        claim.setUpdatedAt(now);
        return claim;
    }
}
