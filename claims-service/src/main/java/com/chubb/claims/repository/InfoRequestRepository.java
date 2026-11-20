package com.chubb.claims.repository;

import com.chubb.claims.domain.InfoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InfoRequestRepository extends JpaRepository<InfoRequest, UUID> {
    List<InfoRequest> findByClaimIdOrderByRequestedAtAsc(UUID claimId);
}
