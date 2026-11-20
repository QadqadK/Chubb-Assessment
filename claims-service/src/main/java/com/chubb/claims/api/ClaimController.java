package com.chubb.claims.api;

import com.chubb.claims.api.dto.ClaimDtos.ClaimDetail;
import com.chubb.claims.api.dto.ClaimDtos.ClaimSummary;
import com.chubb.claims.api.dto.ClaimDtos.CreateClaimRequest;
import com.chubb.claims.api.dto.ClaimDtos.DocumentView;
import com.chubb.claims.api.dto.ClaimDtos.InfoRequestView;
import com.chubb.claims.api.dto.ClaimDtos.RespondInfoRequestRequest;
import com.chubb.claims.security.CurrentUser;
import com.chubb.claims.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @GetMapping
    public List<ClaimSummary> listMine(@AuthenticationPrincipal Jwt jwt) {
        return claimService.listMine(CurrentUser.from(jwt).id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimSummary submit(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody CreateClaimRequest request) {
        return claimService.submit(CurrentUser.from(jwt).id(), request);
    }

    @GetMapping("/{claimId}")
    public ClaimDetail detail(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID claimId) {
        return claimService.getMine(CurrentUser.from(jwt).id(), claimId);
    }

    @PostMapping(value = "/{claimId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView uploadDocument(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID claimId,
                                       @RequestParam("file") MultipartFile file) {
        return claimService.uploadDocument(CurrentUser.from(jwt).id(), claimId, file);
    }

    @PostMapping("/{claimId}/info-requests/{requestId}/response")
    public InfoRequestView respondToInfoRequest(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID claimId,
                                                @PathVariable UUID requestId,
                                                @Valid @RequestBody RespondInfoRequestRequest request) {
        return claimService.respondToInfoRequest(
                CurrentUser.from(jwt).id(), claimId, requestId, request);
    }
}
