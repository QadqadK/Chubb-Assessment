package com.chubb.claims.api;

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
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.security.CurrentUser;
import com.chubb.claims.service.OfficerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OfficerController {

    private final OfficerService officerService;

    @GetMapping("/officer/queue")
    public PageResponse<QueueItem> queue(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) ClaimStatus status,
                                         @RequestParam(required = false) String market,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return officerService.queue(CurrentUser.from(jwt), status, market, page, size);
    }

    @GetMapping("/officer/me/workload")
    public WorkloadResponse workload(@AuthenticationPrincipal Jwt jwt) {
        return officerService.workload(CurrentUser.from(jwt));
    }

    @GetMapping("/officer/claims/{claimId}")
    public OfficerClaimView view(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID claimId) {
        return officerService.view(CurrentUser.from(jwt), claimId);
    }

    @PostMapping("/claims/{claimId}/assign")
    public OfficerClaimView assign(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID claimId) {
        return officerService.assign(CurrentUser.from(jwt), claimId);
    }

    @PostMapping("/claims/{claimId}/info-requests")
    public InfoRequestView requestInformation(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID claimId,
                                              @Valid @RequestBody RequestInformationRequest request) {
        return officerService.requestInformation(CurrentUser.from(jwt), claimId, request);
    }

    @PostMapping("/claims/{claimId}/assessment")
    public AssessmentView assess(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID claimId,
                                 @Valid @RequestBody RecordAssessmentRequest request) {
        return officerService.assess(CurrentUser.from(jwt), claimId, request);
    }

    @PostMapping("/claims/{claimId}/decision")
    public DecisionView decide(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID claimId,
                               @Valid @RequestBody DecisionRequest request) {
        return officerService.decide(CurrentUser.from(jwt), claimId, request);
    }

    @PostMapping("/claims/{claimId}/settle")
    public ClaimSummary settle(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID claimId) {
        return officerService.settle(CurrentUser.from(jwt), claimId);
    }
}
