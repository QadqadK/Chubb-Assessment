package com.chubb.claims.service;

import com.chubb.claims.api.ClaimMapper;
import com.chubb.claims.api.dto.ClaimDtos.ClaimSummary;
import com.chubb.claims.api.dto.ClaimDtos.DecisionView;
import com.chubb.claims.api.dto.ClaimDtos.InfoRequestView;
import com.chubb.claims.api.dto.OfficerDtos.AssessmentView;
import com.chubb.claims.api.dto.OfficerDtos.DecisionRequest;
import com.chubb.claims.api.dto.OfficerDtos.OfficerClaimView;
import com.chubb.claims.api.dto.OfficerDtos.QueueItem;
import com.chubb.claims.api.dto.OfficerDtos.RecordAssessmentRequest;
import com.chubb.claims.api.dto.OfficerDtos.RequestInformationRequest;
import com.chubb.claims.api.dto.OfficerDtos.WorkloadResponse;
import com.chubb.claims.api.dto.PageResponse;
import com.chubb.claims.api.error.ConflictException;
import com.chubb.claims.api.error.ForbiddenOperationException;
import com.chubb.claims.api.error.NotFoundException;
import com.chubb.claims.domain.Assessment;
import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.Decision;
import com.chubb.claims.domain.DecisionOutcome;
import com.chubb.claims.domain.InfoRequest;
import com.chubb.claims.domain.InfoRequestStatus;
import com.chubb.claims.domain.User;
import com.chubb.claims.event.ClaimEventTypes;
import com.chubb.claims.repository.AssessmentRepository;
import com.chubb.claims.repository.ClaimEventRepository;
import com.chubb.claims.repository.ClaimRepository;
import com.chubb.claims.repository.DecisionRepository;
import com.chubb.claims.repository.DocumentRepository;
import com.chubb.claims.repository.InfoRequestRepository;
import com.chubb.claims.repository.UserRepository;
import com.chubb.claims.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfficerService {

    /** Statuses that still contribute to an officer's open workload. */
    private static final Set<ClaimStatus> OPEN_STATUSES = EnumSet.of(
            ClaimStatus.UNDER_REVIEW,
            ClaimStatus.INFO_REQUESTED,
            ClaimStatus.ASSESSED,
            ClaimStatus.APPROVED);

    private final ClaimRepository claims;
    private final UserRepository users;
    private final InfoRequestRepository infoRequests;
    private final AssessmentRepository assessments;
    private final DecisionRepository decisions;
    private final DocumentRepository documents;
    private final ClaimEventRepository events;
    private final ClaimMapper mapper;
    private final ClaimEventRecorder recorder;

    @Transactional(readOnly = true)
    public PageResponse<QueueItem> queue(CurrentUser officer,
                                         ClaimStatus status,
                                         String market,
                                         int page,
                                         int size) {
        String scopedMarket = resolveMarket(officer, market);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "submittedAt"));

        Page<Claim> result = status == null
                ? claims.findByAssignedOfficerIdIsNullAndMarketCode(scopedMarket, pageable)
                : claims.findByAssignedOfficerIdIsNullAndMarketCodeAndStatus(scopedMarket, status, pageable);
        return PageResponse.from(result.map(mapper::toQueueItem));
    }

    @Transactional(readOnly = true)
    public WorkloadResponse workload(CurrentUser officer) {
        List<Claim> assigned = claims.findByAssignedOfficerIdAndStatusInOrderBySubmittedAtAsc(
                officer.id(), OPEN_STATUSES);

        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : OPEN_STATUSES) {
            byStatus.put(status, 0L);
        }
        for (Claim claim : assigned) {
            byStatus.merge(claim.getStatus(), 1L, Long::sum);
        }

        List<QueueItem> oldestOpen = assigned.stream()
                .limit(10)
                .map(mapper::toQueueItem)
                .toList();
        return new WorkloadResponse(officer.id(), assigned.size(), byStatus, oldestOpen);
    }

    @Transactional(readOnly = true)
    public OfficerClaimView view(CurrentUser officer, UUID claimId) {
        return toView(requireMarketClaim(officer, claimId));
    }

    @Transactional
    public OfficerClaimView assign(CurrentUser officer, UUID claimId) {
        Claim claim = requireMarketClaim(officer, claimId);
        if (claim.getAssignedOfficerId() != null) {
            throw new ConflictException("Claim has already been picked up");
        }
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new ConflictException("Only newly submitted claims can be picked up");
        }

        claim.setAssignedOfficerId(officer.id());
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        recorder.record(claim, ClaimEventTypes.CLAIM_ASSIGNED,
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, officer.id(), null,
                Map.of("assignedOfficerId", officer.id()));
        claims.saveAndFlush(claim);
        return toView(claim);
    }

    @Transactional
    public InfoRequestView requestInformation(CurrentUser officer,
                                              UUID claimId,
                                              RequestInformationRequest request) {
        Claim claim = requireAssignedClaim(officer, claimId);
        ClaimStatus before = claim.getStatus();
        if (before != ClaimStatus.UNDER_REVIEW && before != ClaimStatus.INFO_REQUESTED) {
            throw new ConflictException("Information can only be requested while a claim is under review");
        }

        InfoRequest infoRequest = new InfoRequest();
        infoRequest.setClaimId(claimId);
        infoRequest.setRequestedBy(officer.id());
        infoRequest.setQuestion(request.question());
        infoRequest.setStatus(InfoRequestStatus.OPEN);
        infoRequests.save(infoRequest);

        if (before == ClaimStatus.UNDER_REVIEW) {
            claim.transitionTo(ClaimStatus.INFO_REQUESTED);
        }
        recorder.record(claim, ClaimEventTypes.INFO_REQUESTED,
                before, claim.getStatus(), officer.id(), null,
                Map.of("infoRequestId", infoRequest.getId(), "question", request.question()));
        return mapper.toInfoRequest(infoRequest);
    }

    @Transactional
    public AssessmentView assess(CurrentUser officer, UUID claimId, RecordAssessmentRequest request) {
        Claim claim = requireAssignedClaim(officer, claimId);
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new ConflictException(claim.getStatus() == ClaimStatus.INFO_REQUESTED
                    ? "Answer outstanding information requests before assessing"
                    : "Only a claim under review can be assessed");
        }

        Assessment assessment = new Assessment();
        assessment.setClaimId(claimId);
        assessment.setOfficerId(officer.id());
        assessment.setNotes(request.notes());
        assessment.setAssessedAmount(request.assessedAmount());
        assessments.save(assessment);

        claim.setReserveAmount(request.assessedAmount());
        claim.transitionTo(ClaimStatus.ASSESSED);
        recorder.record(claim, ClaimEventTypes.CLAIM_ASSESSED,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.ASSESSED, officer.id(), null,
                Map.of("assessmentId", assessment.getId(),
                        "assessedAmount", request.assessedAmount(),
                        "notes", request.notes()));
        return mapper.toAssessment(assessment);
    }

    @Transactional
    public DecisionView decide(CurrentUser officer, UUID claimId, DecisionRequest request) {
        Claim claim = requireAssignedClaim(officer, claimId);
        if (claim.getStatus() != ClaimStatus.ASSESSED) {
            throw new ConflictException("A claim must be assessed before a decision can be recorded");
        }
        if (decisions.findByClaimId(claimId).isPresent()) {
            throw new ConflictException("A decision has already been recorded for this claim");
        }

        BigDecimal settlementAmount = null;
        if (request.outcome() == DecisionOutcome.APPROVED) {
            settlementAmount = request.settlementAmount() != null
                    ? request.settlementAmount()
                    : assessments.findFirstByClaimIdOrderByCreatedAtDesc(claimId)
                            .map(Assessment::getAssessedAmount)
                            .orElseThrow(() -> new ConflictException(
                                    "No assessment found to base the settlement amount on"));
            claim.transitionTo(ClaimStatus.APPROVED);
        } else {
            claim.transitionTo(ClaimStatus.REJECTED);
        }

        Decision decision = new Decision();
        decision.setClaimId(claimId);
        decision.setOfficerId(officer.id());
        decision.setOutcome(request.outcome());
        decision.setReason(request.reason());
        decision.setSettlementAmount(settlementAmount);
        decisions.save(decision);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("decisionId", decision.getId());
        eventData.put("outcome", request.outcome().name());
        eventData.put("reason", request.reason());
        if (settlementAmount != null) {
            eventData.put("settlementAmount", settlementAmount);
        }
        recorder.record(claim, ClaimEventTypes.CLAIM_DECIDED,
                ClaimStatus.ASSESSED, claim.getStatus(), officer.id(), request.reason(), eventData);
        return mapper.toDecision(decision);
    }

    @Transactional
    public ClaimSummary settle(CurrentUser officer, UUID claimId) {
        Claim claim = requireAssignedClaim(officer, claimId);
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ConflictException("Only approved claims can be settled");
        }

        claim.transitionTo(ClaimStatus.SETTLED);
        recorder.record(claim, ClaimEventTypes.CLAIM_SETTLED,
                ClaimStatus.APPROVED, ClaimStatus.SETTLED, officer.id(), null);
        return mapper.toSummary(claim);
    }

    private OfficerClaimView toView(Claim claim) {
        User claimant = users.findById(claim.getClaimantId()).orElse(null);
        return mapper.toOfficerView(
                claim,
                claimant,
                events.findByClaimIdOrderByOccurredAtAsc(claim.getId()),
                infoRequests.findByClaimIdOrderByRequestedAtAsc(claim.getId()),
                documents.findByClaimIdOrderByUploadedAtAsc(claim.getId()),
                assessments.findFirstByClaimIdOrderByCreatedAtDesc(claim.getId()).orElse(null),
                decisions.findByClaimId(claim.getId()).orElse(null));
    }

    private Claim requireMarketClaim(CurrentUser officer, UUID claimId) {
        Claim claim = claims.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.getMarketCode().equalsIgnoreCase(officer.marketCode())) {
            throw new ForbiddenOperationException("Claim is outside your market");
        }
        return claim;
    }

    private Claim requireAssignedClaim(CurrentUser officer, UUID claimId) {
        Claim claim = requireMarketClaim(officer, claimId);
        if (!officer.id().equals(claim.getAssignedOfficerId())) {
            throw new ForbiddenOperationException("This claim is not assigned to you");
        }
        return claim;
    }

    private static String resolveMarket(CurrentUser officer, String requestedMarket) {
        if (requestedMarket == null || requestedMarket.isBlank()) {
            return officer.marketCode();
        }
        String market = requestedMarket.trim().toUpperCase(Locale.ROOT);
        if (!market.equals(officer.marketCode())) {
            throw new ForbiddenOperationException("You can only view claims in your market");
        }
        return market;
    }
}
