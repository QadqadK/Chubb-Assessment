package com.chubb.claims.api;

import com.chubb.claims.api.dto.ClaimDtos.ClaimDetail;
import com.chubb.claims.api.dto.ClaimDtos.ClaimEventView;
import com.chubb.claims.api.dto.ClaimDtos.ClaimSummary;
import com.chubb.claims.api.dto.ClaimDtos.DecisionView;
import com.chubb.claims.api.dto.ClaimDtos.DocumentView;
import com.chubb.claims.api.dto.ClaimDtos.InfoRequestView;
import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimEvent;
import com.chubb.claims.domain.Decision;
import com.chubb.claims.domain.Document;
import com.chubb.claims.domain.InfoRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaimMapper {

    public ClaimSummary toSummary(Claim claim) {
        return new ClaimSummary(
                claim.getId(),
                claim.getReference(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getMarketCode(),
                claim.getIncidentDate(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getSubmittedAt(),
                claim.getUpdatedAt());
    }

    public ClaimDetail toDetail(Claim claim,
                                List<ClaimEvent> events,
                                List<InfoRequest> infoRequests,
                                List<Document> documents,
                                Decision decision) {
        return new ClaimDetail(
                claim.getId(),
                claim.getReference(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getMarketCode(),
                claim.getIncidentDate(),
                claim.getIncidentLocation(),
                claim.getDescription(),
                claim.getIncidentDetails(),
                claim.getCurrency(),
                claim.getClaimedAmount(),
                claim.getSubmittedAt(),
                claim.getUpdatedAt(),
                claim.getClosedAt(),
                events.stream().map(this::toEvent).toList(),
                infoRequests.stream().map(this::toInfoRequest).toList(),
                documents.stream().map(this::toDocument).toList(),
                decision == null ? null : toDecision(decision));
    }

    public ClaimEventView toEvent(ClaimEvent event) {
        return new ClaimEventView(
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getNote(),
                event.getOccurredAt());
    }

    public InfoRequestView toInfoRequest(InfoRequest request) {
        return new InfoRequestView(
                request.getId(),
                request.getQuestion(),
                request.getResponse(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getRespondedAt());
    }

    public DocumentView toDocument(Document document) {
        return new DocumentView(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getUploadedAt());
    }

    public DecisionView toDecision(Decision decision) {
        return new DecisionView(
                decision.getOutcome(),
                decision.getReason(),
                decision.getSettlementAmount(),
                decision.getDecidedAt());
    }
}
