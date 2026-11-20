package com.chubb.claims.repository;

import com.chubb.claims.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chubb.claims.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {
    List<Claim> findByClaimantIdOrderBySubmittedAtDesc(UUID claimantId);

    List<Claim> findByAssignedOfficerIdAndStatusInOrderBySubmittedAtAsc(
            UUID officerId, Collection<ClaimStatus> statuses);

    Page<Claim> findByAssignedOfficerIdIsNullAndMarketCode(String marketCode, Pageable pageable);

    Page<Claim> findByAssignedOfficerIdIsNullAndMarketCodeAndStatus(
            String marketCode, ClaimStatus status, Pageable pageable);

    @Query(value = "SELECT nextval('claims.claim_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();
}
