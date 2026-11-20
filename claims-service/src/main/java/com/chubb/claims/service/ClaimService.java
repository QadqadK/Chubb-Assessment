package com.chubb.claims.service;

import com.chubb.claims.api.ClaimMapper;
import com.chubb.claims.api.dto.ClaimDtos.ClaimDetail;
import com.chubb.claims.api.dto.ClaimDtos.ClaimSummary;
import com.chubb.claims.api.dto.ClaimDtos.CreateClaimRequest;
import com.chubb.claims.api.dto.ClaimDtos.DocumentView;
import com.chubb.claims.api.dto.ClaimDtos.InfoRequestView;
import com.chubb.claims.api.dto.ClaimDtos.RespondInfoRequestRequest;
import com.chubb.claims.api.error.BadRequestException;
import com.chubb.claims.api.error.ConflictException;
import com.chubb.claims.api.error.ForbiddenOperationException;
import com.chubb.claims.api.error.NotFoundException;
import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimEvent;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.Document;
import com.chubb.claims.domain.InfoRequest;
import com.chubb.claims.domain.InfoRequestStatus;
import com.chubb.claims.domain.Policy;
import com.chubb.claims.domain.User;
import com.chubb.claims.repository.ClaimEventRepository;
import com.chubb.claims.repository.ClaimRepository;
import com.chubb.claims.repository.DecisionRepository;
import com.chubb.claims.repository.DocumentRepository;
import com.chubb.claims.repository.InfoRequestRepository;
import com.chubb.claims.repository.PolicyRepository;
import com.chubb.claims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final UserRepository users;
    private final InfoRequestRepository infoRequests;
    private final DocumentRepository documents;
    private final ClaimEventRepository events;
    private final DecisionRepository decisions;
    private final ClaimMapper mapper;
    private final DocumentStorage documentStorage;

    @Transactional
    public ClaimSummary submit(UUID claimantId, CreateClaimRequest request) {
        User claimant = users.findById(claimantId)
                .filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("Claimant not found"));

        Policy policy = policies.findById(request.policyId())
                .orElseThrow(() -> new NotFoundException("Policy not found"));

        if (!policy.getHolderId().equals(claimant.getId())) {
            throw new ForbiddenOperationException("This policy does not belong to you");
        }
        if (request.incidentDate().isBefore(policy.getStartDate())
                || request.incidentDate().isAfter(policy.getEndDate())) {
            throw new BadRequestException("Incident date is outside the policy period");
        }

        Claim claim = new Claim();
        claim.setReference("CLM-%s-%06d".formatted(policy.getMarketCode(), claims.nextReferenceNumber()));
        claim.setPolicyId(policy.getId());
        claim.setClaimantId(claimant.getId());
        claim.setMarketCode(policy.getMarketCode());
        claim.setClaimType(policy.getPolicyType());
        claim.setIncidentDate(request.incidentDate());
        claim.setIncidentLocation(request.incidentLocation());
        claim.setDescription(request.description());
        claim.setIncidentDetails(request.incidentDetails() == null
                ? new HashMap<>()
                : new HashMap<>(request.incidentDetails()));
        claim.setCurrency(policy.getCurrency());
        claim.setClaimedAmount(request.claimedAmount());

        Claim saved = claims.save(claim);
        recordEvent(saved, "CLAIM_SUBMITTED", null, ClaimStatus.SUBMITTED, claimantId, null);
        return mapper.toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<ClaimSummary> listMine(UUID claimantId) {
        return claims.findByClaimantIdOrderBySubmittedAtDesc(claimantId).stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimDetail getMine(UUID claimantId, UUID claimId) {
        Claim claim = requireOwnedClaim(claimantId, claimId);
        return mapper.toDetail(
                claim,
                events.findByClaimIdOrderByOccurredAtAsc(claimId),
                infoRequests.findByClaimIdOrderByRequestedAtAsc(claimId),
                documents.findByClaimIdOrderByUploadedAtAsc(claimId),
                decisions.findByClaimId(claimId).orElse(null));
    }

    @Transactional
    public InfoRequestView respondToInfoRequest(UUID claimantId,
                                                UUID claimId,
                                                UUID requestId,
                                                RespondInfoRequestRequest request) {
        Claim claim = requireOwnedClaim(claimantId, claimId);

        if (claim.getStatus() != ClaimStatus.INFO_REQUESTED) {
            throw new ConflictException("This claim is not awaiting information");
        }

        InfoRequest infoRequest = infoRequests.findById(requestId)
                .filter(candidate -> candidate.getClaimId().equals(claimId))
                .orElseThrow(() -> new NotFoundException("Information request not found"));

        if (infoRequest.getStatus() != InfoRequestStatus.OPEN) {
            throw new ConflictException("This information request has already been answered");
        }

        infoRequest.setResponse(request.response());
        infoRequest.setStatus(InfoRequestStatus.ANSWERED);
        infoRequest.setRespondedAt(Instant.now());
        infoRequests.saveAndFlush(infoRequest);

        boolean stillOpen = infoRequests.countByClaimIdAndStatus(claimId, InfoRequestStatus.OPEN) > 0;
        if (!stillOpen) {
            claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        }
        recordEvent(claim, "INFO_PROVIDED", ClaimStatus.INFO_REQUESTED, claim.getStatus(), claimantId, null);

        return mapper.toInfoRequest(infoRequest);
    }

    @Transactional
    public DocumentView uploadDocument(UUID claimantId, UUID claimId, MultipartFile file) {
        Claim claim = requireOwnedClaim(claimantId, claimId);
        DocumentStorage.StoredFile stored = documentStorage.store(claimId, file);

        Document document = new Document();
        document.setClaimId(claimId);
        document.setUploadedBy(claimantId);
        document.setFileName(stored.fileName());
        document.setContentType(stored.contentType());
        document.setSizeBytes(stored.sizeBytes());
        document.setStoragePath(stored.storagePath());

        Document saved = documents.save(document);
        recordEvent(claim, "DOCUMENT_UPLOADED", null, null, claimantId, stored.fileName());
        return mapper.toDocument(saved);
    }

    private Claim requireOwnedClaim(UUID claimantId, UUID claimId) {
        Claim claim = claims.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.getClaimantId().equals(claimantId)) {
            // Return 404 rather than 403 so a claimant cannot probe for other people's claim IDs.
            throw new NotFoundException("Claim not found");
        }
        return claim;
    }

    private void recordEvent(Claim claim,
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
